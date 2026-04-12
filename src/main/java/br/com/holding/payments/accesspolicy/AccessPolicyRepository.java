package br.com.holding.payments.accesspolicy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessPolicyRepository extends JpaRepository<AccessPolicy, Long> {

    Optional<AccessPolicy> findByCompanyId(Long companyId);
}
