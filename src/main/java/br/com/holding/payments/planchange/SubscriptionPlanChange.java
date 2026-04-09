package br.com.holding.payments.planchange;

import br.com.holding.payments.charge.Charge;
import br.com.holding.payments.common.errors.IllegalStateTransitionException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.creditledger.CustomerCreditLedger;
import br.com.holding.payments.plan.Plan;
import br.com.holding.payments.subscription.Subscription;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_plan_changes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlanChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_plan_id", nullable = false)
    private Plan previousPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_plan_id", nullable = false)
    private Plan requestedPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private PlanChangeType changeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlanChangePolicy policy;

    @Column(name = "delta_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal deltaAmount = BigDecimal.ZERO;

    @Column(name = "proration_credit", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal prorationCredit = BigDecimal.ZERO;

    @Column(name = "proration_charge", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal prorationCharge = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PlanChangeStatus status = PlanChangeStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id")
    private Charge charge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_ledger_id")
    private CustomerCreditLedger creditLedgerEntry;

    @Column(name = "scheduled_for")
    private LocalDateTime scheduledFor;

    @Column(name = "effective_at")
    private LocalDateTime effectiveAt;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    public void transitionTo(PlanChangeStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateTransitionException(this.id, this.status, target);
        }
        this.status = target;
    }

    public void markEffective() {
        transitionTo(PlanChangeStatus.EFFECTIVE);
        this.effectiveAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        transitionTo(PlanChangeStatus.FAILED);
        this.failureReason = reason;
    }
}
