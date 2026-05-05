package br.com.holding.payments.asaassync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AsaasSyncRetryJob {

    private final AsaasSyncService asaasSyncService;

    @Value("${app.asaas-sync.batch-size:25}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.asaas-sync.relay-interval-ms:10000}")
    public void processPending() {
        List<Long> ids = asaasSyncService.fetchReadyBatchIds(batchSize);
        if (ids.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending Asaas sync entries", ids.size());

        for (Long id : ids) {
            try {
                asaasSyncService.processOne(id);
            } catch (Exception e) {
                // processOne ja captura e lida com falhas internamente.
                // Esse catch e uma rede de seguranca para nao parar o job.
                log.error("Falha inesperada processando AsaasSyncPending id={}: {}",
                        id, e.getMessage(), e);
            }
        }
    }
}
