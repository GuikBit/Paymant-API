package br.com.holding.payments.auth;

import br.com.holding.payments.common.BaseEntity;
import br.com.holding.payments.company.Company;
import jakarta.persistence.*;
import lombok.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String roles;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

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
}
