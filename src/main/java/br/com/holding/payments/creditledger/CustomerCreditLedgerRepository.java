package br.com.holding.payments.creditledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerCreditLedgerRepository extends JpaRepository<CustomerCreditLedger, Long> {

    Page<CustomerCreditLedger> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
}
