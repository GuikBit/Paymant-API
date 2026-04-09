package br.com.holding.payments.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Customer c WHERE c.id = :id")
    Optional<Customer> findByIdWithLock(@Param("id") Long id);

    Optional<Customer> findByDocument(String document);

    Optional<Customer> findByAsaasId(String asaasId);

    boolean existsByDocument(String document);

    @Query("SELECT c FROM Customer c WHERE " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "c.document LIKE CONCAT('%', :search, '%') OR " +
            "LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Customer> search(@Param("search") String search, Pageable pageable);

    @Query(value = "SELECT * FROM customers WHERE id = :id", nativeQuery = true)
    Optional<Customer> findByIdIncludingDeleted(@Param("id") Long id);
}
