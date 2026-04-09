package br.com.holding.payments.webhook;

import br.com.holding.payments.company.Company;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_events",
        uniqueConstraints = @UniqueConstraint(name = "uq_webhook_event",
                columnNames = {"asaas_event_id", "company_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "asaas_event_id", nullable = false)
    private String asaasEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WebhookEventStatus status = WebhookEventStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "received_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();

    public void markProcessing() {
        this.status = WebhookEventStatus.PROCESSING;
    }

    public void markProcessed() {
        this.status = WebhookEventStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    public void markDeferred(String reason, long backoffSeconds) {
        this.status = WebhookEventStatus.DEFERRED;
        this.attemptCount++;
        this.nextAttemptAt = LocalDateTime.now().plusSeconds(backoffSeconds);
        this.lastError = reason;
    }

    public void markFailed(String error) {
        this.status = WebhookEventStatus.FAILED;
        this.attemptCount++;
        this.lastError = error;
    }

    public void markDlq(String error) {
        this.status = WebhookEventStatus.DLQ;
        this.lastError = error;
    }

    public void markReadyForRetry() {
        this.status = WebhookEventStatus.PENDING;
        this.nextAttemptAt = LocalDateTime.now();
    }
}
