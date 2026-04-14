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

    @Column(name = "processed_resource_type", length = 40)
    private String processedResourceType;

    @Column(name = "processed_resource_id")
    private Long processedResourceId;

    @Column(name = "processed_asaas_id")
    private String processedAsaasId;

    @Column(name = "processing_summary", columnDefinition = "TEXT")
    private String processingSummary;

    @Column(name = "processing_duration_ms")
    private Long processingDurationMs;

    public void markProcessing() {
        this.status = WebhookEventStatus.PROCESSING;
    }

    public void markProcessed(String summary) {
        this.status = WebhookEventStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
        this.processingSummary = summary;
        this.processingDurationMs = java.time.Duration.between(receivedAt, processedAt).toMillis();
    }

    public void linkResource(String resourceType, Long resourceId, String asaasId) {
        this.processedResourceType = resourceType;
        this.processedResourceId = resourceId;
        this.processedAsaasId = asaasId;
    }

    public void markDeferred(String reason, long backoffSeconds) {
        this.status = WebhookEventStatus.DEFERRED;
        this.attemptCount++;
        this.nextAttemptAt = LocalDateTime.now().plusSeconds(backoffSeconds);
        this.lastError = reason;
        this.processingSummary = reason;
    }

    public void markFailed(String error) {
        this.status = WebhookEventStatus.FAILED;
        this.attemptCount++;
        this.lastError = error;
        this.processingSummary = "Falha: " + error;
    }

    public void markDlq(String error) {
        this.status = WebhookEventStatus.DLQ;
        this.lastError = error;
        this.processingSummary = "Movido para DLQ: " + error;
    }

    public void markReadyForRetry() {
        this.status = WebhookEventStatus.PENDING;
        this.nextAttemptAt = LocalDateTime.now();
    }
}
