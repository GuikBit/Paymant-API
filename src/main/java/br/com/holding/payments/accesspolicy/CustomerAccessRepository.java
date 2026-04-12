package br.com.holding.payments.accesspolicy;

import br.com.holding.payments.charge.Charge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CustomerAccessRepository extends JpaRepository<Charge, Long> {

    // Cobrancas vencidas de assinatura (subscription_id NOT NULL)
    @Query(value = """
            SELECT COUNT(*) FROM charges
            WHERE customer_id = :customerId
              AND status = 'OVERDUE'
              AND subscription_id IS NOT NULL
              AND due_date <= :toleranceDate
            """, nativeQuery = true)
    long countOverdueSubscriptionCharges(@Param("customerId") Long customerId,
                                         @Param("toleranceDate") LocalDate toleranceDate);

    // Cobrancas vencidas avulsas (subscription_id IS NULL)
    @Query(value = """
            SELECT COUNT(*) FROM charges
            WHERE customer_id = :customerId
              AND status = 'OVERDUE'
              AND subscription_id IS NULL
              AND due_date <= :toleranceDate
            """, nativeQuery = true)
    long countOverdueStandaloneCharges(@Param("customerId") Long customerId,
                                       @Param("toleranceDate") LocalDate toleranceDate);

    // Total de cobrancas vencidas (qualquer tipo)
    @Query(value = """
            SELECT COUNT(*) FROM charges
            WHERE customer_id = :customerId
              AND status = 'OVERDUE'
              AND due_date <= :toleranceDate
            """, nativeQuery = true)
    long countAllOverdueCharges(@Param("customerId") Long customerId,
                                @Param("toleranceDate") LocalDate toleranceDate);

    // Valor total de cobrancas vencidas
    @Query(value = """
            SELECT COALESCE(SUM(value), 0) FROM charges
            WHERE customer_id = :customerId
              AND status = 'OVERDUE'
            """, nativeQuery = true)
    BigDecimal sumOverdueValue(@Param("customerId") Long customerId);

    // Dias desde a cobranca vencida mais antiga
    @Query(value = """
            SELECT COALESCE(EXTRACT(DAY FROM NOW() - MIN(due_date))::INT, 0)
            FROM charges
            WHERE customer_id = :customerId
              AND status = 'OVERDUE'
            """, nativeQuery = true)
    int oldestOverdueDays(@Param("customerId") Long customerId);

    // Contagem de assinaturas por status
    @Query(value = """
            SELECT COUNT(*) FROM subscriptions
            WHERE customer_id = :customerId AND status = :status
            """, nativeQuery = true)
    long countSubscriptionsByStatus(@Param("customerId") Long customerId,
                                    @Param("status") String status);
}
