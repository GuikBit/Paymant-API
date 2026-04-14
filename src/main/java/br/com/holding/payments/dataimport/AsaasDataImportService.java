package br.com.holding.payments.dataimport;

import br.com.holding.payments.charge.BillingType;
import br.com.holding.payments.charge.Charge;
import br.com.holding.payments.charge.ChargeOrigin;
import br.com.holding.payments.charge.ChargeRepository;
import br.com.holding.payments.charge.ChargeStatus;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.customer.CustomerRepository;
import br.com.holding.payments.dataimport.dto.ImportResult;
import br.com.holding.payments.installment.Installment;
import br.com.holding.payments.installment.InstallmentRepository;
import br.com.holding.payments.integration.asaas.client.AsaasCustomerClient;
import br.com.holding.payments.integration.asaas.client.AsaasPaymentClient;
import br.com.holding.payments.integration.asaas.client.AsaasSubscriptionClient;
import br.com.holding.payments.integration.asaas.dto.AsaasCustomerResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasPageResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasPaymentResponse;
import br.com.holding.payments.integration.asaas.dto.AsaasSubscriptionResponse;
import br.com.holding.payments.plan.Plan;
import br.com.holding.payments.plan.PlanRepository;
import br.com.holding.payments.subscription.Subscription;
import br.com.holding.payments.subscription.SubscriptionRepository;
import br.com.holding.payments.subscription.SubscriptionStatus;
import br.com.holding.payments.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsaasDataImportService {

    private static final int PAGE_SIZE = 100;

    private final AsaasCustomerClient customerClient;
    private final AsaasPaymentClient paymentClient;
    private final AsaasSubscriptionClient subscriptionClient;
    private final CustomerRepository customerRepository;
    private final ChargeRepository chargeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InstallmentRepository installmentRepository;
    private final PlanRepository planRepository;
    private final CompanyRepository companyRepository;
    private final EntityManager entityManager;

    private static final String IMPORTED_PLAN_CODIGO = "IMPORTED_ASAAS";

    @Transactional
    public ImportResult importAllFromAsaas() {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.getReferenceById(companyId);

        LocalDateTime startedAt = LocalDateTime.now();
        List<String> errors = new ArrayList<>();

        log.info("Iniciando importacao de dados do Asaas para company_id={}", companyId);

        // 1. Importar clientes
        ImportCounters customerCounters = importCustomers(companyId, company, errors);

        // 2. Importar assinaturas (precisa dos clientes já importados)
        ImportCounters subscriptionCounters = importSubscriptions(companyId, company, errors);

        // 3. Importar cobranças/pagamentos
        ImportCounters chargeCounters = importCharges(companyId, company, errors);

        LocalDateTime finishedAt = LocalDateTime.now();

        log.info("Importacao finalizada para company_id={}. Clientes: {}/{}, Assinaturas: {}/{}, Cobrancas: {}/{}",
                companyId,
                customerCounters.imported, customerCounters.imported + customerCounters.skipped,
                subscriptionCounters.imported, subscriptionCounters.imported + subscriptionCounters.skipped,
                chargeCounters.imported, chargeCounters.imported + chargeCounters.skipped);

        return new ImportResult(
                customerCounters.imported, customerCounters.skipped,
                subscriptionCounters.imported, subscriptionCounters.skipped,
                chargeCounters.imported, chargeCounters.skipped,
                chargeCounters.installmentsImported, chargeCounters.installmentsSkipped,
                errors, startedAt, finishedAt
        );
    }

    private ImportCounters importCustomers(Long companyId, Company company, List<String> errors) {
        int imported = 0;
        int skipped = 0;
        int offset = 0;

        log.info("Importando clientes do Asaas...");

        while (true) {
            AsaasPageResponse<AsaasCustomerResponse> page = customerClient.list(companyId, offset, PAGE_SIZE);

            for (AsaasCustomerResponse asaasCustomer : page.data()) {
                try {
                    if (Boolean.TRUE.equals(asaasCustomer.deleted())) {
                        skipped++;
                        continue;
                    }

                    if (customerRepository.findByAsaasId(asaasCustomer.id()).isPresent()) {
                        skipped++;
                        continue;
                    }

                    // Validar campos obrigatórios
                    if (asaasCustomer.cpfCnpj() == null || asaasCustomer.cpfCnpj().isBlank()) {
                        errors.add("Cliente " + asaasCustomer.id() + " ignorado: cpfCnpj nulo ou vazio");
                        skipped++;
                        continue;
                    }

                    if (asaasCustomer.name() == null || asaasCustomer.name().isBlank()) {
                        errors.add("Cliente " + asaasCustomer.id() + " ignorado: nome nulo ou vazio");
                        skipped++;
                        continue;
                    }

                    // Verificar se já existe pelo documento
                    if (customerRepository.findByDocument(asaasCustomer.cpfCnpj()).isPresent()) {
                        skipped++;
                        continue;
                    }

                    Customer customer = Customer.builder()
                            .company(company)
                            .asaasId(asaasCustomer.id())
                            .name(asaasCustomer.name())
                            .document(asaasCustomer.cpfCnpj())
                            .email(asaasCustomer.email())
                            .phone(asaasCustomer.phone() != null ? asaasCustomer.phone() : asaasCustomer.mobilePhone())
                            .addressStreet(asaasCustomer.address())
                            .addressNumber(asaasCustomer.addressNumber())
                            .addressComplement(asaasCustomer.complement())
                            .addressNeighborhood(asaasCustomer.province())
                            .addressCity(asaasCustomer.city())
                            .addressState(asaasCustomer.state())
                            .addressPostalCode(asaasCustomer.postalCode())
                            .build();

                    customerRepository.saveAndFlush(customer);
                    imported++;
                } catch (Exception e) {
                    entityManager.clear();
                    String msg = "Erro ao importar cliente asaas_id=" + asaasCustomer.id() + ": " + e.getMessage();
                    log.warn(msg, e);
                    errors.add(msg);
                }
            }

            if (!page.hasMore()) break;
            offset += PAGE_SIZE;
        }

        log.info("Clientes importados: {}, ignorados: {}", imported, skipped);
        return new ImportCounters(imported, skipped, 0, 0);
    }

    private ImportCounters importSubscriptions(Long companyId, Company company, List<String> errors) {
        int imported = 0;
        int skipped = 0;
        int offset = 0;

        // Plano placeholder para assinaturas importadas (plan_id é NOT NULL)
        Plan importedPlan = getOrCreateImportedPlan(companyId, company);

        log.info("Importando assinaturas do Asaas...");

        while (true) {
            AsaasPageResponse<AsaasSubscriptionResponse> page = subscriptionClient.list(companyId, offset, PAGE_SIZE);

            for (AsaasSubscriptionResponse asaasSub : page.data()) {
                try {
                    if (Boolean.TRUE.equals(asaasSub.deleted())) {
                        skipped++;
                        continue;
                    }

                    if (subscriptionRepository.findByAsaasId(asaasSub.id()).isPresent()) {
                        skipped++;
                        continue;
                    }

                    // Buscar o cliente local pelo asaasId do customer
                    Customer customer = customerRepository.findByAsaasId(asaasSub.customer()).orElse(null);
                    if (customer == null) {
                        String msg = "Assinatura " + asaasSub.id() + " ignorada: cliente " + asaasSub.customer() + " nao encontrado localmente";
                        log.warn(msg);
                        errors.add(msg);
                        skipped++;
                        continue;
                    }

                    Subscription subscription = Subscription.builder()
                            .company(company)
                            .customer(customer)
                            .plan(importedPlan)
                            .asaasId(asaasSub.id())
                            .billingType(parseBillingType(asaasSub.billingType()))
                            .cycle(parseCycle(asaasSub.cycle()))
                            .effectivePrice(asaasSub.value())
                            .nextDueDate(asaasSub.nextDueDate() != null ? LocalDate.parse(asaasSub.nextDueDate()) : null)
                            .status(parseSubscriptionStatus(asaasSub.status()))
                            .build();

                    subscriptionRepository.saveAndFlush(subscription);
                    imported++;
                } catch (Exception e) {
                    entityManager.clear();
                    String msg = "Erro ao importar assinatura asaas_id=" + asaasSub.id() + ": " + e.getMessage();
                    log.warn(msg, e);
                    errors.add(msg);
                }
            }

            if (!page.hasMore()) break;
            offset += PAGE_SIZE;
        }

        log.info("Assinaturas importadas: {}, ignoradas: {}", imported, skipped);
        return new ImportCounters(imported, skipped, 0, 0);
    }

    private Plan getOrCreateImportedPlan(Long companyId, Company company) {
        if (planRepository.existsByCodigoAndCompanyId(IMPORTED_PLAN_CODIGO, companyId)) {
            return planRepository.findAll().stream()
                    .filter(p -> IMPORTED_PLAN_CODIGO.equals(p.getCodigo()))
                    .findFirst()
                    .orElseThrow();
        }

        Plan plan = Plan.builder()
                .company(company)
                .name("Plano Importado (Asaas)")
                .description("Plano placeholder criado automaticamente durante a importacao de dados do Asaas. "
                        + "Atualize as assinaturas importadas para vincular aos planos corretos.")
                .codigo(IMPORTED_PLAN_CODIGO)
                .precoMensal(BigDecimal.ZERO)
                .active(false)
                .build();

        return planRepository.save(plan);
    }

    private ImportCounters importCharges(Long companyId, Company company, List<String> errors) {
        int imported = 0;
        int skipped = 0;
        int installmentsImported = 0;
        int installmentsSkipped = 0;
        int offset = 0;

        // Cache de installments já criados: asaasInstallmentId -> Installment local
        Map<String, Installment> installmentCache = new HashMap<>();

        log.info("Importando cobrancas do Asaas...");

        while (true) {
            AsaasPageResponse<AsaasPaymentResponse> page = paymentClient.list(companyId, offset, PAGE_SIZE);

            for (AsaasPaymentResponse asaasPayment : page.data()) {
                try {
                    if (Boolean.TRUE.equals(asaasPayment.deleted())) {
                        skipped++;
                        continue;
                    }

                    if (chargeRepository.findByAsaasId(asaasPayment.id()).isPresent()) {
                        skipped++;
                        continue;
                    }

                    // Buscar cliente local
                    Customer customer = customerRepository.findByAsaasId(asaasPayment.customer()).orElse(null);
                    if (customer == null) {
                        String msg = "Cobranca " + asaasPayment.id() + " ignorada: cliente " + asaasPayment.customer() + " nao encontrado localmente";
                        log.warn(msg);
                        errors.add(msg);
                        skipped++;
                        continue;
                    }

                    // Resolver installment se existir
                    Installment installment = null;
                    if (asaasPayment.installment() != null) {
                        installment = installmentCache.get(asaasPayment.installment());
                        if (installment == null) {
                            // Verificar se já existe no banco
                            installment = installmentRepository.findByAsaasId(asaasPayment.installment()).orElse(null);
                            if (installment == null) {
                                installment = Installment.builder()
                                        .company(company)
                                        .customer(customer)
                                        .asaasId(asaasPayment.installment())
                                        .totalValue(asaasPayment.value())
                                        .installmentCount(asaasPayment.installmentNumber() != null ? asaasPayment.installmentNumber() : 1)
                                        .billingType(parseBillingType(asaasPayment.billingType()))
                                        .build();
                                installment = installmentRepository.saveAndFlush(installment);
                                installmentsImported++;
                            } else {
                                installmentsSkipped++;
                            }
                            installmentCache.put(asaasPayment.installment(), installment);
                        }
                    }

                    // Vincular a assinatura local quando o pagamento veio de uma subscription do Asaas
                    Long subscriptionId = null;
                    if (asaasPayment.subscription() != null && !asaasPayment.subscription().isBlank()) {
                        Subscription localSub = subscriptionRepository.findByAsaasId(asaasPayment.subscription()).orElse(null);
                        if (localSub != null) {
                            subscriptionId = localSub.getId();
                        } else {
                            String msg = "Cobranca " + asaasPayment.id() + ": assinatura " + asaasPayment.subscription()
                                    + " ainda nao importada localmente, charge sera salva sem vinculo";
                            log.warn(msg);
                            errors.add(msg);
                        }
                    }

                    Charge charge = Charge.builder()
                            .company(company)
                            .customer(customer)
                            .asaasId(asaasPayment.id())
                            .billingType(parseBillingType(asaasPayment.billingType()))
                            .value(asaasPayment.value())
                            .dueDate(asaasPayment.dueDate() != null ? LocalDate.parse(asaasPayment.dueDate()) : LocalDate.now())
                            .status(parseChargeStatus(asaasPayment.status()))
                            .origin(ChargeOrigin.API)
                            .externalReference(asaasPayment.externalReference())
                            .invoiceUrl(asaasPayment.invoiceUrl())
                            .boletoUrl(asaasPayment.bankSlipUrl())
                            .installment(installment)
                            .installmentNumber(asaasPayment.installmentNumber())
                            .subscriptionId(subscriptionId)
                            .build();

                    chargeRepository.saveAndFlush(charge);
                    imported++;
                } catch (Exception e) {
                    entityManager.clear();
                    String msg = "Erro ao importar cobranca asaas_id=" + asaasPayment.id() + ": " + e.getMessage();
                    log.warn(msg, e);
                    errors.add(msg);
                }
            }

            if (!page.hasMore()) break;
            offset += PAGE_SIZE;
        }

        log.info("Cobrancas importadas: {}, ignoradas: {}. Parcelamentos importados: {}, ignorados: {}",
                imported, skipped, installmentsImported, installmentsSkipped);
        return new ImportCounters(imported, skipped, installmentsImported, installmentsSkipped);
    }

    private BillingType parseBillingType(String billingType) {
        if (billingType == null) return BillingType.UNDEFINED;
        return switch (billingType.toUpperCase()) {
            case "PIX" -> BillingType.PIX;
            case "CREDIT_CARD" -> BillingType.CREDIT_CARD;
            case "DEBIT_CARD" -> BillingType.DEBIT_CARD;
            case "BOLETO" -> BillingType.BOLETO;
            default -> BillingType.UNDEFINED;
        };
    }

    private ChargeStatus parseChargeStatus(String status) {
        if (status == null) return ChargeStatus.PENDING;
        return switch (status.toUpperCase()) {
            case "PENDING" -> ChargeStatus.PENDING;
            case "CONFIRMED" -> ChargeStatus.CONFIRMED;
            case "RECEIVED" -> ChargeStatus.RECEIVED;
            case "OVERDUE" -> ChargeStatus.OVERDUE;
            case "REFUNDED" -> ChargeStatus.REFUNDED;
            case "RECEIVED_IN_CASH" -> ChargeStatus.RECEIVED;
            case "REFUND_REQUESTED", "REFUND_IN_PROGRESS" -> ChargeStatus.REFUNDED;
            case "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE", "AWAITING_CHARGEBACK_REVERSAL" -> ChargeStatus.CHARGEBACK;
            case "DUNNING_REQUESTED", "DUNNING_RECEIVED" -> ChargeStatus.OVERDUE;
            case "AWAITING_RISK_ANALYSIS" -> ChargeStatus.PENDING;
            default -> ChargeStatus.PENDING;
        };
    }

    private SubscriptionStatus parseSubscriptionStatus(String status) {
        if (status == null) return SubscriptionStatus.ACTIVE;
        return switch (status.toUpperCase()) {
            case "ACTIVE" -> SubscriptionStatus.ACTIVE;
            case "INACTIVE", "EXPIRED" -> SubscriptionStatus.EXPIRED;
            default -> SubscriptionStatus.ACTIVE;
        };
    }

    private br.com.holding.payments.plan.PlanCycle parseCycle(String cycle) {
        if (cycle == null) return br.com.holding.payments.plan.PlanCycle.MONTHLY;
        return switch (cycle.toUpperCase()) {
            case "MONTHLY" -> br.com.holding.payments.plan.PlanCycle.MONTHLY;
            case "SEMIANNUALLY" -> br.com.holding.payments.plan.PlanCycle.SEMIANNUALLY;
            case "YEARLY" -> br.com.holding.payments.plan.PlanCycle.YEARLY;
            default -> br.com.holding.payments.plan.PlanCycle.MONTHLY;
        };
    }

    private record ImportCounters(int imported, int skipped, int installmentsImported, int installmentsSkipped) {}
}
