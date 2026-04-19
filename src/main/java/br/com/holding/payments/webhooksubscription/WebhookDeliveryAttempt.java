package br.com.holding.payments.webhooksubscription;

import br.com.holding.payments.company.Company;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_delivery_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private WebhookSubscription subscription;

    @Column(name = "outbox_event_id")
    private Long outboxEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body_excerpt", columnDefinition = "TEXT")
    private String responseBodyExcerpt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Builder.Default
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private WebhookDeliveryStatus status = WebhookDeliveryStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.nextAttemptAt == null) this.nextAttemptAt = this.createdAt;
    }

    public void markDelivered(int responseStatus, String responseExcerpt, long durationMs) {
        this.status = WebhookDeliveryStatus.DELIVERED;
        this.responseStatus = responseStatus;
        this.responseBodyExcerpt = responseExcerpt;
        this.durationMs = durationMs;
        this.deliveredAt = LocalDateTime.now();
    }

    public void markFailed(Integer responseStatus, String responseExcerpt, long durationMs,
                           String error, LocalDateTime nextAttemptAt) {
        this.status = WebhookDeliveryStatus.FAILED;
        this.responseStatus = responseStatus;
        this.responseBodyExcerpt = responseExcerpt;
        this.durationMs = durationMs;
        this.errorMessage = error;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markPendingRetry(LocalDateTime nextAttemptAt) {
        this.status = WebhookDeliveryStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markDlq(String error) {
        this.status = WebhookDeliveryStatus.DLQ;
        this.errorMessage = error;
    }
}
