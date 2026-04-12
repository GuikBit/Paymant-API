package br.com.holding.payments.apikey;

import br.com.holding.payments.auth.Role;
import br.com.holding.payments.common.BaseEntity;
import br.com.holding.payments.company.Company;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, length = 128)
    private String keyHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private String roles;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public Set<Role> getRoleSet() {
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .map(Role::valueOf)
                .collect(Collectors.toSet());
    }

    public void setRoleSet(Set<Role> roleSet) {
        this.roles = roleSet.stream()
                .map(Role::name)
                .collect(Collectors.joining(","));
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return Boolean.TRUE.equals(active) && !isExpired();
    }
}
