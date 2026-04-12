package br.com.holding.payments.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmailAndActiveTrue(String email);

    boolean existsByEmail(String email);

    Page<UserEntity> findByCompanyId(Long companyId, Pageable pageable);
}
