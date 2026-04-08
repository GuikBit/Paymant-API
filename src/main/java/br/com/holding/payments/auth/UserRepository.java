package br.com.holding.payments.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmailAndActiveTrue(String email);

    boolean existsByEmail(String email);
}
