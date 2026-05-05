package br.com.holding.payments.asaassync;

import br.com.holding.payments.integration.asaas.gateway.AsaasGatewayService;
import br.com.holding.payments.subscription.Subscription;
import br.com.holding.payments.tenant.CrossTenant;
import br.com.holding.payments.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Servico de sincronizacao assincrona com a API do Asaas.
 *
 * O metodo {@link #enqueueSubscriptionValueUpdate} enfileira a operacao na
 * tabela asaas_sync_pending dentro da transacao do chamador. Isso garante
 * que a entrada da fila so e persistida se a transacao do dominio commitar
 * (consistencia outbox-style: se a mudanca de plano local rola back, o
 * pedido de sync no Asaas tambem some).
 *
 * O job {@link AsaasSyncRetryJob} consome a fila com backoff exponencial.
 */
@Service
@Slf4j
public class AsaasSyncService {

    private final AsaasSyncPendingRepository repository;
    private final AsaasGatewayService asaasGateway;
    private final int defaultMaxAttempts;
    private final long initialBackoffSeconds;
    private final Counter enqueuedCounter;
    private final Counter retrySuccessCounter;
    private final Counter retryFailureCounter;
    private final Counter deadCounter;

    public AsaasSyncService(AsaasSyncPendingRepository repository,
                            AsaasGatewayService asaasGateway,
                            @Value("${app.asaas-sync.max-attempts:8}") int defaultMaxAttempts,
                            @Value("${app.asaas-sync.initial-backoff-seconds:60}") long initialBackoffSeconds,
                            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.asaasGateway = asaasGateway;
        this.defaultMaxAttempts = defaultMaxAttempts;
        this.initialBackoffSeconds = initialBackoffSeconds;
        this.enqueuedCounter = Counter.builder("asaas_sync_enqueued_total")
                .description("Total Asaas sync operations enqueued")
                .register(meterRegistry);
        this.retrySuccessCounter = Counter.builder("asaas_sync_retry_success_total")
                .description("Total successful Asaas sync retries")
                .register(meterRegistry);
        this.retryFailureCounter = Counter.builder("asaas_sync_retry_failure_total")
                .description("Total failed Asaas sync retries")
                .register(meterRegistry);
        this.deadCounter = Counter.builder("asaas_sync_dead_total")
                .description("Total Asaas sync operations moved to DEAD")
                .register(meterRegistry);
    }

    /**
     * Enfileira atualizacao do valor recorrente da assinatura no Asaas.
     * Roda na transacao do chamador para garantir atomicidade com a mudanca
     * local. Nao faz chamada de rede aqui.
     *
     * Se subscription.asaasId for null (ex.: plano free), nao faz nada --
     * nao ha o que sincronizar.
     */
    public void enqueueSubscriptionValueUpdate(Subscription subscription, BigDecimal newValue, String correlationId) {
        if (subscription == null || subscription.getAsaasId() == null) {
            return;
        }
        if (newValue == null) {
            log.warn("enqueueSubscriptionValueUpdate chamado com newValue=null; ignorando. subscriptionId={}",
                    subscription.getId());
            return;
        }

        AsaasSyncPending pending = AsaasSyncPending.builder()
                .company(subscription.getCompany())
                .subscription(subscription)
                .asaasId(subscription.getAsaasId())
                .operation(AsaasSyncOperation.UPDATE_SUBSCRIPTION_VALUE)
                .targetValue(newValue)
                .correlationId(correlationId)
                .status(AsaasSyncStatus.PENDING)
                .attempts(0)
                .maxAttempts(defaultMaxAttempts)
                .nextAttemptAt(LocalDateTime.now())
                .build();
        repository.save(pending);
        enqueuedCounter.increment();
        log.info("Asaas sync enqueued: subscriptionId={}, asaasId={}, value={}, correlation={}",
                subscription.getId(), subscription.getAsaasId(), newValue, correlationId);
    }

    /**
     * Busca IDs de pendencias prontas para retry. Cross-tenant pois o batch
     * pode atravessar varias companies.
     */
    @Transactional(readOnly = true)
    @CrossTenant(reason = "AsaasSyncRetryJob coleta pendencias de varios tenants")
    public List<Long> fetchReadyBatchIds(int batchSize) {
        return repository.findReadyForRetry(batchSize)
                .stream()
                .map(AsaasSyncPending::getId)
                .toList();
    }

    /**
     * Processa uma unica pendencia. Cada chamada e uma transacao independente
     * para isolar falhas entre entradas. Roda cross-tenant para que o RLS
     * nao filtre a entrada (a operacao alvo e direcionada por company_id
     * explicito do registro, nao pelo TenantContext).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CrossTenant(reason = "Retry de operacao Asaas atravessa tenants no batch")
    public void processOne(Long entryId) {
        AsaasSyncPending entry = repository.findById(entryId).orElse(null);
        if (entry == null || entry.getStatus() != AsaasSyncStatus.PENDING) {
            return;
        }

        Long previousCompanyId = TenantContext.getCompanyId();
        try {
            TenantContext.setCompanyId(entry.getCompany().getId());
            executeOperation(entry);
        } catch (Exception e) {
            handleRetryFailure(entry, e);
        } finally {
            if (previousCompanyId == null) {
                TenantContext.clear();
            } else {
                TenantContext.setCompanyId(previousCompanyId);
            }
        }
    }

    private void executeOperation(AsaasSyncPending entry) {
        Long companyId = entry.getCompany().getId();
        switch (entry.getOperation()) {
            case UPDATE_SUBSCRIPTION_VALUE -> {
                asaasGateway.updateSubscriptionValue(companyId, entry.getAsaasId(), entry.getTargetValue());
                entry.markDone();
                repository.save(entry);
                retrySuccessCounter.increment();
                log.info("Asaas sync OK: id={}, op={}, asaasId={}, value={}",
                        entry.getId(), entry.getOperation(), entry.getAsaasId(), entry.getTargetValue());
            }
        }
    }

    private void handleRetryFailure(AsaasSyncPending entry, Exception e) {
        String error = errorMessage(e);
        retryFailureCounter.increment();

        int newAttempts = (entry.getAttempts() == null ? 0 : entry.getAttempts()) + 1;
        if (newAttempts >= entry.getMaxAttempts()) {
            entry.markDead(error);
            deadCounter.increment();
            log.error("Asaas sync DEAD after {} attempts: id={}, op={}, asaasId={}, error={}",
                    entry.getMaxAttempts(), entry.getId(), entry.getOperation(), entry.getAsaasId(), error);
        } else {
            entry.recordFailure(error, computeNextAttempt(newAttempts));
            log.warn("Asaas sync failed (attempt {}/{}): id={}, asaasId={}, error={}",
                    newAttempts, entry.getMaxAttempts(), entry.getId(), entry.getAsaasId(), error);
        }
        repository.save(entry);
    }

    private LocalDateTime computeNextAttempt(int attemptNumber) {
        // Backoff exponencial limitado: initial * 2^(n-1), saturado em 2^10 (~17min se initial=60s)
        int safeShift = Math.min(Math.max(attemptNumber - 1, 0), 10);
        long delaySeconds = initialBackoffSeconds * (1L << safeShift);
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }

    private static String errorMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
