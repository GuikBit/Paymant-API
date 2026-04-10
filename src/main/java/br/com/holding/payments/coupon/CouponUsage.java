package br.com.holding.payments.coupon;

import br.com.holding.payments.company.Company;
import br.com.holding.payments.customer.Customer;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_usages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "charge_id")
    private Long chargeId;

    @Column(name = "original_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal originalValue;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "final_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalValue;

    @Column(name = "plan_code", length = 50)
    private String planCode;

    @Column(length = 20)
    private String cycle;

    @Builder.Default
    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt = LocalDateTime.now();
}
