package br.com.holding.payments.charge;

import br.com.holding.payments.common.errors.IllegalStateTransitionException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.customer.Customer;
import br.com.holding.payments.installment.Installment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "charges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Charge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_id")
    private Installment installment;

    @Column(name = "asaas_id", length = 100)
    private String asaasId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 20)
    private BillingType billingType;

    @Column(nullable = false)
    private BigDecimal value;

    // Coupon
    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "original_value", precision = 12, scale = 2)
    private BigDecimal originalValue;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ChargeStatus status = ChargeStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ChargeOrigin origin = ChargeOrigin.API;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "pix_qrcode")
    private String pixQrcode;

    @Column(name = "pix_copy_paste")
    private String pixCopyPaste;

    @Column(name = "boleto_url")
    private String boletoUrl;

    @Column(name = "invoice_url")
    private String invoiceUrl;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void transitionTo(ChargeStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalStateTransitionException(this.id, this.status, target);
        }
        this.status = target;
    }
}
