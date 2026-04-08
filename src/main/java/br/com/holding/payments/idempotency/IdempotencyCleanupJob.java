package br.com.holding.payments.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupJob {

    private final IdempotencyService idempotencyService;

    @Scheduled(fixedRate = 3600000) // every hour
    public void cleanupExpiredKeys() {
        int deleted = idempotencyService.cleanupExpired();
        if (deleted > 0) {
            log.info("Cleaned up {} expired idempotency keys", deleted);
        }
    }
}
