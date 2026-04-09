package br.com.holding.payments.config;

import br.com.holding.payments.outbox.OutboxEventRepository;
import br.com.holding.payments.webhook.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@RequiredArgsConstructor
public class CustomHealthIndicators {

    private final OutboxEventRepository outboxEventRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final RedisConnectionFactory redisConnectionFactory;

    @Bean
    public HealthIndicator outboxLagHealth() {
        return () -> {
            try {
                Double lagSeconds = outboxEventRepository.calculateLagSeconds();
                double lag = lagSeconds != null ? lagSeconds : 0.0;

                if (lag > 300) {
                    return Health.down()
                            .withDetail("lag_seconds", lag)
                            .withDetail("status", "Outbox lag acima de 5 minutos")
                            .build();
                } else if (lag > 60) {
                    return Health.status("WARNING")
                            .withDetail("lag_seconds", lag)
                            .withDetail("status", "Outbox lag elevado")
                            .build();
                }
                return Health.up()
                        .withDetail("lag_seconds", lag)
                        .build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    @Bean
    public HealthIndicator webhookDlqHealth() {
        return () -> {
            try {
                long dlqCount = webhookEventRepository.countDlq();
                long deferredCount = webhookEventRepository.countDeferred();

                Health.Builder builder = dlqCount > 0 ? Health.down() : Health.up();
                return builder
                        .withDetail("dlq_count", dlqCount)
                        .withDetail("deferred_count", deferredCount)
                        .build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    @Bean
    public HealthIndicator redisHealth() {
        return () -> {
            try {
                String pong = redisConnectionFactory.getConnection().ping();
                return Health.up()
                        .withDetail("response", pong)
                        .build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("error", "Redis connection failed: " + e.getMessage())
                        .build();
            }
        };
    }
}
