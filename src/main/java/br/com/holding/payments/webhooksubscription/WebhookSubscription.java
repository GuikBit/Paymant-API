package br.com.holding.payments.webhooksubscription;

import br.com.holding.payments.common.BaseEntity;
import br.com.holding.payments.company.Company;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String tokenEncrypted;

    @Column(name = "token_prefix", nullable = false, length = 12)
    private String tokenPrefix;

    @Column(name = "event_types", nullable = false, columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] eventTypes;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "last_delivery_at")
    private LocalDateTime lastDeliveryAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    public boolean subscribesTo(String eventType) {
        if (eventTypes == null) return false;
        for (String t : eventTypes) {
            if (WebhookEventCatalog.WILDCARD.equals(t) || t.equals(eventType)) return true;
        }
        return false;
    }
}
