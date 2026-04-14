package br.com.holding.payments.dataimport;

import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.company.AsaasEnvironment;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.dataimport.dto.WipeResult;
import br.com.holding.payments.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrgDataWipeService {

    // Ordem importa: filhos antes dos pais para respeitar FKs.
    private static final List<String> WIPE_ORDER = List.of(
            "audit_log",
            "subscription_plan_changes",
            "customer_credit_ledger",
            "charges",
            "installments",
            "subscriptions",
            "plans",
            "webhook_events",
            "outbox",
            "idempotency_keys",
            "customers"
    );

    private final CompanyRepository companyRepository;
    private final EntityManager entityManager;

    @Transactional
    public WipeResult wipeAll() {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BusinessException("Empresa nao encontrada: " + companyId));

        if (company.getAsaasEnv() != AsaasEnvironment.SANDBOX) {
            throw new BusinessException(
                    "Limpeza de dados so eh permitida em empresas com ambiente Asaas SANDBOX. "
                            + "Empresa atual esta em: " + company.getAsaasEnv());
        }

        log.warn("Iniciando WIPE de dados para company_id={} ({})",
                companyId, company.getRazaoSocial());

        Map<String, Integer> deletedByTable = new LinkedHashMap<>();
        for (String table : WIPE_ORDER) {
            int affected = entityManager.createNativeQuery(
                            "DELETE FROM " + table + " WHERE company_id = :cid")
                    .setParameter("cid", companyId)
                    .executeUpdate();
            deletedByTable.put(table, affected);
            log.info("Wipe company_id={} -> {} linhas removidas de {}", companyId, affected, table);
        }

        int total = deletedByTable.values().stream().mapToInt(Integer::intValue).sum();
        log.warn("Wipe finalizado para company_id={}: {} linhas removidas no total", companyId, total);

        return new WipeResult(companyId, total, deletedByTable);
    }
}
