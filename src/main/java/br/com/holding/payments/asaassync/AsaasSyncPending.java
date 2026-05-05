package br.com.holding.payments.asaassync;

import br.com.holding.payments.company.Company;
import br.com.holding.payments.subscription.Subscription;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "asaas_sync_pending")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsaasSyncPending {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(name = "asaas_id", nullable = false, length = 100)
    private String asaasId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AsaasSyncOperation operation;

    @Column(name = "target_value", precision = 12, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AsaasSyncStatus status = AsaasSyncStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 8;

    @Column(name = "next_attempt_at", nullable = false)
    @Builder.Default
    private LocalDateTime nextAttemptAt = LocalDateTime.now();

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public void markDone() {
        this.status = AsaasSyncStatus.DONE;
        this.completedAt = LocalDateTime.now();
    }

    public void markDead(String reason) {
        this.status = AsaasSyncStatus.DEAD;
        this.lastError = reason;
        this.completedAt = LocalDateTime.now();
    }

    public void recordFailure(String error, LocalDateTime nextAttemptAt) {
        this.attempts = (this.attempts == null ? 0 : this.attempts) + 1;
        this.lastError = error;
        this.nextAttemptAt = nextAttemptAt;
    }
}
