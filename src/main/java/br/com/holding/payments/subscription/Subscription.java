package br.com.holding.payments.subscription;

import br.com.holding.payments.charge.BillingType;
import br.com.holding.payments.common.errors.IllegalStateTransitionException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.plan.Plan;
import br.com.holding.payments.plan.PlanCycle;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "asaas_id", length = 100)
    private String asaasId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 20)
    private BillingType billingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanCycle cycle;

    @Column(name = "effective_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal effectivePrice;

    // Coupon
    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "coupon_discount_amount", precision = 12, scale = 2)
    private BigDecimal couponDiscountAmount;

    @Column(name = "coupon_uses_remaining")
    private Integer couponUsesRemaining;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void transitionTo(SubscriptionStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateTransitionException(this.id, this.status, target);
        }
        this.status = target;
    }
}
