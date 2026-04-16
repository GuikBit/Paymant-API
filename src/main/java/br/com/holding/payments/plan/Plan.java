package br.com.holding.payments.plan;

import br.com.holding.payments.company.Company;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "plans")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String name;

    private String description;

    // Identification
    @Column(nullable = false, length = 50)
    private String codigo;

    // Pricing
    @Column(name = "preco_mensal", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoMensal;

    @Column(name = "preco_anual", precision = 12, scale = 2)
    private BigDecimal precoAnual;

    @Column(name = "desconto_percentual_anual", precision = 5, scale = 2)
    private BigDecimal descontoPercentualAnual;

    // Promo Mensal
    @Builder.Default
    @Column(name = "promo_mensal_ativa", nullable = false)
    private Boolean promoMensalAtiva = false;

    @Column(name = "promo_mensal_preco", precision = 12, scale = 2)
    private BigDecimal promoMensalPreco;

    @Column(name = "promo_mensal_texto", length = 100)
    private String promoMensalTexto;

    @Column(name = "promo_mensal_inicio")
    private LocalDateTime promoMensalInicio;

    @Column(name = "promo_mensal_fim")
    private LocalDateTime promoMensalFim;

    // Promo Anual
    @Builder.Default
    @Column(name = "promo_anual_ativa", nullable = false)
    private Boolean promoAnualAtiva = false;

    @Column(name = "promo_anual_preco", precision = 12, scale = 2)
    private BigDecimal promoAnualPreco;

    @Column(name = "promo_anual_texto", length = 100)
    private String promoAnualTexto;

    @Column(name = "promo_anual_inicio")
    private LocalDateTime promoAnualInicio;

    @Column(name = "promo_anual_fim")
    private LocalDateTime promoAnualFim;

    @Builder.Default
    @Column(name = "trial_days", nullable = false)
    private Integer trialDays = 0;

    @Builder.Default
    @Column(name = "setup_fee", nullable = false)
    private BigDecimal setupFee = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "is_free", nullable = false)
    private Boolean isFree = false;

    @Builder.Default
    @Column(nullable = false)
    private Integer version = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String limits;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String features;

    @Builder.Default
    @Column(name = "tier_order", nullable = false)
    private Integer tierOrder = 0;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
