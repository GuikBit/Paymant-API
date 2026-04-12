package br.com.holding.payments.accesspolicy;

import br.com.holding.payments.common.BaseEntity;
import br.com.holding.payments.company.Company;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "access_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessPolicy extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Builder.Default
    @Column(name = "max_overdue_charges", nullable = false)
    private Integer maxOverdueCharges = 1;

    @Builder.Default
    @Column(name = "overdue_tolerance_days", nullable = false)
    private Integer overdueToleranceDays = 0;

    @Builder.Default
    @Column(name = "block_on_suspended_subscription", nullable = false)
    private Boolean blockOnSuspendedSubscription = true;

    @Builder.Default
    @Column(name = "block_on_standalone_charges", nullable = false)
    private Boolean blockOnStandaloneCharges = false;

    @Builder.Default
    @Column(name = "block_on_subscription_charges", nullable = false)
    private Boolean blockOnSubscriptionCharges = true;

    @Builder.Default
    @Column(name = "block_on_negative_credit", nullable = false)
    private Boolean blockOnNegativeCredit = false;

    @Column(name = "custom_block_message")
    private String customBlockMessage;

    @Builder.Default
    @Column(name = "cache_ttl_minutes", nullable = false)
    private Integer cacheTtlMinutes = 5;
}
