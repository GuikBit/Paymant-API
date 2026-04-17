package br.com.holding.payments.dataimport;

import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.company.AsaasEnvironment;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.dataimport.dto.WipeCategory;
import br.com.holding.payments.dataimport.dto.WipeRequest;
import br.com.holding.payments.dataimport.dto.WipeResult;
import br.com.holding.payments.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrgDataWipeService {

    // Mapeamento categoria -> tabela fisica.
    private static final Map<WipeCategory, String> TABLE = new EnumMap<>(WipeCategory.class);
    static {
        TABLE.put(WipeCategory.CUSTOMERS, "customers");
        TABLE.put(WipeCategory.SUBSCRIPTIONS, "subscriptions");
        TABLE.put(WipeCategory.PLANS, "plans");
        TABLE.put(WipeCategory.CHARGES, "charges");
        TABLE.put(WipeCategory.INSTALLMENTS, "installments");
        TABLE.put(WipeCategory.PLAN_CHANGES, "subscription_plan_changes");
        TABLE.put(WipeCategory.CREDIT_LEDGER, "customer_credit_ledger");
        TABLE.put(WipeCategory.WEBHOOK_EVENTS, "webhook_events");
        TABLE.put(WipeCategory.OUTBOX, "outbox");
        TABLE.put(WipeCategory.IDEMPOTENCY_KEYS, "idempotency_keys");
        TABLE.put(WipeCategory.AUDIT_LOG, "audit_log");
    }

    // Dependencias HARD (FKs NOT NULL): se A esta na lista, B precisa tambem estar (B referencia A com NOT NULL).
    private static final Map<WipeCategory, Set<WipeCategory>> HARD_DEPENDENTS = new EnumMap<>(WipeCategory.class);
    static {
        // customers eh NOT NULL em: subscriptions, charges, installments, customer_credit_ledger
        HARD_DEPENDENTS.put(WipeCategory.CUSTOMERS, EnumSet.of(
                WipeCategory.SUBSCRIPTIONS,
                WipeCategory.CHARGES,
                WipeCategory.INSTALLMENTS,
                WipeCategory.CREDIT_LEDGER));
        // subscriptions eh NOT NULL em: subscription_plan_changes
        HARD_DEPENDENTS.put(WipeCategory.SUBSCRIPTIONS, EnumSet.of(
                WipeCategory.PLAN_CHANGES));
        // plans eh NOT NULL em: subscriptions e subscription_plan_changes (previous/requested)
        HARD_DEPENDENTS.put(WipeCategory.PLANS, EnumSet.of(
                WipeCategory.SUBSCRIPTIONS,
                WipeCategory.PLAN_CHANGES));
    }

    // Ordem de DELETE respeitando FKs (filhos antes dos pais).
    private static final List<WipeCategory> DELETE_ORDER = List.of(
            WipeCategory.AUDIT_LOG,
            WipeCategory.PLAN_CHANGES,
            WipeCategory.CREDIT_LEDGER,
            WipeCategory.CHARGES,
            WipeCategory.INSTALLMENTS,
            WipeCategory.SUBSCRIPTIONS,
            WipeCategory.PLANS,
            WipeCategory.WEBHOOK_EVENTS,
            WipeCategory.OUTBOX,
            WipeCategory.IDEMPOTENCY_KEYS,
            WipeCategory.CUSTOMERS
    );

    private final CompanyRepository companyRepository;
    private final EntityManager entityManager;

    @Transactional
    public WipeResult wipe(WipeRequest request) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException("Empresa nao encontrada: " + companyId));

        if (company.getAsaasEnv() != AsaasEnvironment.SANDBOX) {
            throw new BusinessException(
                    "Limpeza de dados so eh permitida em empresas com ambiente Asaas SANDBOX. "
                            + "Empresa atual esta em: " + company.getAsaasEnv());
        }

        Set<WipeCategory> requested = (request == null || request.categories() == null || request.categories().isEmpty())
                ? EnumSet.allOf(WipeCategory.class)
                : EnumSet.copyOf(request.categories());

        Set<WipeCategory> effective = resolveDependencies(requested);

        log.warn("Iniciando WIPE company_id={} ({}) requested={} effective={}",
                companyId, company.getRazaoSocial(), requested, effective);

        // FKs NULLABLE: setar NULL antes de deletar o lado referenciado.
        nullifyOptionalReferences(companyId, effective);

        Map<String, Integer> deletedByTable = new LinkedHashMap<>();
        for (WipeCategory cat : DELETE_ORDER) {
            if (!effective.contains(cat)) {
                continue;
            }
            String table = TABLE.get(cat);
            int affected = entityManager.createNativeQuery(
                            "DELETE FROM " + table + " WHERE company_id = :cid")
                    .setParameter("cid", companyId)
                    .executeUpdate();
            deletedByTable.put(table, affected);
            log.info("Wipe company_id={} -> {} linhas removidas de {}", companyId, affected, table);
        }

        int total = deletedByTable.values().stream().mapToInt(Integer::intValue).sum();
        log.warn("Wipe finalizado company_id={}: {} linhas removidas no total", companyId, total);

        return new WipeResult(companyId, total, requested, effective, deletedByTable);
    }

    /**
     * Fecho transitivo das dependencias hard (FKs NOT NULL).
     */
    private Set<WipeCategory> resolveDependencies(Set<WipeCategory> requested) {
        Set<WipeCategory> result = EnumSet.copyOf(requested);
        Deque<WipeCategory> queue = new ArrayDeque<>(requested);
        while (!queue.isEmpty()) {
            WipeCategory current = queue.poll();
            Set<WipeCategory> deps = HARD_DEPENDENTS.getOrDefault(current, EnumSet.noneOf(WipeCategory.class));
            for (WipeCategory dep : deps) {
                if (result.add(dep)) {
                    queue.add(dep);
                }
            }
        }
        return result;
    }

    /**
     * Para FKs NULLABLE, quando o lado referenciado vai ser deletado mas o
     * lado referenciador NAO foi selecionado, setamos a FK para NULL.
     */
    private void nullifyOptionalReferences(Long companyId, Set<WipeCategory> effective) {
        // charges.subscription_id (NULLABLE) -> se CHARGES nao esta no set e SUBSCRIPTIONS esta
        if (effective.contains(WipeCategory.SUBSCRIPTIONS) && !effective.contains(WipeCategory.CHARGES)) {
            nullify("UPDATE charges SET subscription_id = NULL WHERE company_id = :cid AND subscription_id IS NOT NULL", companyId);
        }
        // charges.installment_id (NULLABLE) -> se CHARGES nao esta no set e INSTALLMENTS esta
        if (effective.contains(WipeCategory.INSTALLMENTS) && !effective.contains(WipeCategory.CHARGES)) {
            nullify("UPDATE charges SET installment_id = NULL WHERE company_id = :cid AND installment_id IS NOT NULL", companyId);
        }
        // subscription_plan_changes.charge_id (NULLABLE) -> se PLAN_CHANGES nao esta e CHARGES esta
        if (effective.contains(WipeCategory.CHARGES) && !effective.contains(WipeCategory.PLAN_CHANGES)) {
            nullify("UPDATE subscription_plan_changes SET charge_id = NULL WHERE company_id = :cid AND charge_id IS NOT NULL", companyId);
        }
        // subscription_plan_changes.credit_ledger_id (NULLABLE) -> se PLAN_CHANGES nao esta e CREDIT_LEDGER esta
        if (effective.contains(WipeCategory.CREDIT_LEDGER) && !effective.contains(WipeCategory.PLAN_CHANGES)) {
            nullify("UPDATE subscription_plan_changes SET credit_ledger_id = NULL WHERE company_id = :cid AND credit_ledger_id IS NOT NULL", companyId);
        }
    }

    private void nullify(String sql, Long companyId) {
        int n = entityManager.createNativeQuery(sql).setParameter("cid", companyId).executeUpdate();
        if (n > 0) {
            log.info("Wipe company_id={} -> {} FKs setadas para NULL ({})", companyId, n, sql);
        }
    }
}
