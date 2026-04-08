package br.com.holding.payments.company;

import br.com.holding.payments.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company extends BaseEntity {

    @Column(nullable = false, unique = true, length = 18)
    private String cnpj;

    @Column(name = "razao_social", nullable = false)
    private String razaoSocial;

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "asaas_api_key_encrypted", columnDefinition = "TEXT")
    private String asaasApiKeyEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(name = "asaas_env", nullable = false, length = 20)
    @Builder.Default
    private AsaasEnvironment asaasEnv = AsaasEnvironment.SANDBOX;

    @Column(name = "webhook_token")
    private String webhookToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CompanyStatus status = CompanyStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_change_policy", nullable = false, length = 30)
    @Builder.Default
    private PlanChangePolicy planChangePolicy = PlanChangePolicy.IMMEDIATE_PRORATA;

    @Enumerated(EnumType.STRING)
    @Column(name = "downgrade_validation_strategy", nullable = false, length = 20)
    @Builder.Default
    private DowngradeValidationStrategy downgradeValidationStrategy = DowngradeValidationStrategy.BLOCK;

    @Column(name = "grace_period_days", nullable = false)
    @Builder.Default
    private Integer gracePeriodDays = 0;
}
