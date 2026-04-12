package br.com.holding.payments.accesspolicy;

import br.com.holding.payments.accesspolicy.dto.AccessStatusResponse;
import br.com.holding.payments.accesspolicy.dto.AccessStatusResponse.AccessSummary;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.customer.CustomerRepository;
import br.com.holding.payments.outbox.OutboxPublisher;
import br.com.holding.payments.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerAccessService {

    private static final String CACHE_PREFIX = "access_status:";

    private final CustomerAccessRepository accessRepository;
    private final CustomerRepository customerRepository;
    private final AccessPolicyService accessPolicyService;
    private final OutboxPublisher outboxPublisher;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public AccessStatusResponse checkAccess(Long customerId) {
        Long companyId = TenantContext.getRequiredCompanyId();

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        AccessPolicy policy = accessPolicyService.getOrCreateDefault(companyId);

        // Calcular data de tolerancia: cobranças vencidas há mais de X dias
        LocalDate toleranceDate = LocalDate.now().minusDays(policy.getOverdueToleranceDays());

        // Coletar métricas
        long overdueSubscriptionCharges = accessRepository.countOverdueSubscriptionCharges(customerId, toleranceDate);
        long overdueStandaloneCharges = accessRepository.countOverdueStandaloneCharges(customerId, toleranceDate);
        long totalOverdueCharges = accessRepository.countAllOverdueCharges(customerId, toleranceDate);
        BigDecimal totalOverdueValue = accessRepository.sumOverdueValue(customerId);
        int oldestOverdueDays = accessRepository.oldestOverdueDays(customerId);
        long activeSubscriptions = accessRepository.countSubscriptionsByStatus(customerId, "ACTIVE");
        long suspendedSubscriptions = accessRepository.countSubscriptionsByStatus(customerId, "SUSPENDED");
        BigDecimal creditBalance = customer.getCreditBalance();

        // Avaliar regras de bloqueio
        List<String> reasons = new ArrayList<>();

        // Regra 1: Cobrancas de assinatura vencidas
        if (Boolean.TRUE.equals(policy.getBlockOnSubscriptionCharges())
                && overdueSubscriptionCharges >= policy.getMaxOverdueCharges()) {
            reasons.add(overdueSubscriptionCharges + " cobranca(s) de assinatura vencida(s) ha mais de "
                    + policy.getOverdueToleranceDays() + " dia(s)");
        }

        // Regra 2: Cobrancas avulsas vencidas
        if (Boolean.TRUE.equals(policy.getBlockOnStandaloneCharges())
                && overdueStandaloneCharges >= policy.getMaxOverdueCharges()) {
            reasons.add(overdueStandaloneCharges + " cobranca(s) avulsa(s) vencida(s) ha mais de "
                    + policy.getOverdueToleranceDays() + " dia(s)");
        }

        // Regra 3: Assinatura suspensa
        if (Boolean.TRUE.equals(policy.getBlockOnSuspendedSubscription())
                && suspendedSubscriptions > 0) {
            reasons.add(suspendedSubscriptions + " assinatura(s) suspensa(s) por inadimplencia");
        }

        // Regra 4: Saldo de credito negativo
        if (Boolean.TRUE.equals(policy.getBlockOnNegativeCredit())
                && creditBalance.compareTo(BigDecimal.ZERO) < 0) {
            reasons.add("Saldo de credito negativo: " + creditBalance);
        }

        boolean allowed = reasons.isEmpty();

        AccessSummary summary = new AccessSummary(
                activeSubscriptions,
                suspendedSubscriptions,
                totalOverdueCharges,
                totalOverdueValue,
                oldestOverdueDays,
                creditBalance
        );

        AccessStatusResponse response = new AccessStatusResponse(
                customerId,
                customer.getName(),
                allowed,
                reasons,
                allowed ? null : policy.getCustomBlockMessage(),
                summary,
                LocalDateTime.now()
        );

        // Cache no Redis (write-through)
        cacheResponse(companyId, customerId, response, policy.getCacheTtlMinutes());

        // Verificar se houve mudanca de status para gerar evento
        checkAndPublishStatusChange(companyId, customerId, allowed, response);

        return response;
    }

    private void cacheResponse(Long companyId, Long customerId, AccessStatusResponse response, int ttlMinutes) {
        try {
            String key = CACHE_PREFIX + companyId + ":" + customerId;
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(ttlMinutes));
        } catch (Exception e) {
            log.warn("Falha ao cachear access status no Redis: customer_id={}: {}", customerId, e.getMessage());
        }
    }

    private void checkAndPublishStatusChange(Long companyId, Long customerId, boolean allowed,
                                              AccessStatusResponse response) {
        try {
            String statusKey = CACHE_PREFIX + "last_status:" + companyId + ":" + customerId;
            String previousStatus = redisTemplate.opsForValue().get(statusKey);
            String currentStatus = allowed ? "ALLOWED" : "BLOCKED";

            if (previousStatus != null && !previousStatus.equals(currentStatus)) {
                outboxPublisher.publish(
                        "CustomerAccessStatusChanged",
                        "Customer",
                        customerId.toString(),
                        response
                );
                log.info("Status de acesso alterado: customer_id={}, {} -> {}",
                        customerId, previousStatus, currentStatus);
            }

            redisTemplate.opsForValue().set(statusKey, currentStatus);
        } catch (Exception e) {
            log.warn("Falha ao verificar mudanca de status: customer_id={}: {}", customerId, e.getMessage());
        }
    }
}
