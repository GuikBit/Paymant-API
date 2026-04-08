package br.com.holding.payments.installment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    Optional<Installment> findByAsaasId(String asaasId);
}
