package br.com.holding.payments.report;

import br.com.holding.payments.charge.Charge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReportRepository extends JpaRepository<Charge, Long> {

    // ==================== REVENUE ====================

    @Query(value = """
            SELECT c.billing_type AS group_key,
                   COUNT(*) AS charge_count,
                   COALESCE(SUM(c.value), 0) AS total_value
            FROM charges c
            WHERE c.status IN ('CONFIRMED', 'RECEIVED')
              AND c.due_date >= :from AND c.due_date <= :to
            GROUP BY c.billing_type
            ORDER BY total_value DESC
            """, nativeQuery = true)
    List<Object[]> revenueByMethod(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = """
            SELECT TO_CHAR(c.due_date, 'YYYY-MM-DD') AS group_key,
                   COUNT(*) AS charge_count,
                   COALESCE(SUM(c.value), 0) AS total_value
            FROM charges c
            WHERE c.status IN ('CONFIRMED', 'RECEIVED')
              AND c.due_date >= :from AND c.due_date <= :to
            GROUP BY TO_CHAR(c.due_date, 'YYYY-MM-DD')
            ORDER BY group_key
            """, nativeQuery = true)
    List<Object[]> revenueByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = """
            SELECT c.origin AS group_key,
                   COUNT(*) AS charge_count,
                   COALESCE(SUM(c.value), 0) AS total_value
            FROM charges c
            WHERE c.status IN ('CONFIRMED', 'RECEIVED')
              AND c.due_date >= :from AND c.due_date <= :to
            GROUP BY c.origin
            ORDER BY total_value DESC
            """, nativeQuery = true)
    List<Object[]> revenueByOrigin(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ==================== MRR ====================

    @Query(value = """
            SELECT COALESCE(SUM(s.effective_price - COALESCE(s.coupon_discount_amount, 0)), 0)
            FROM subscriptions s
            WHERE s.status = 'ACTIVE'
            """, nativeQuery = true)
    BigDecimal calculateMrr();

    @Query(value = """
            SELECT COUNT(*)
            FROM subscriptions s
            WHERE s.status = 'ACTIVE'
            """, nativeQuery = true)
    long countActiveSubscriptions();

    // ==================== CHURN ====================

    @Query(value = """
            SELECT COUNT(*)
            FROM subscriptions s
            WHERE s.status = 'CANCELED'
              AND s.updated_at >= CAST(:from AS TIMESTAMP)
              AND s.updated_at < CAST(:to AS TIMESTAMP)
            """, nativeQuery = true)
    long countCanceledInPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = """
            SELECT COUNT(*)
            FROM subscriptions s
            WHERE s.created_at < CAST(:date AS TIMESTAMP)
              AND (s.status = 'ACTIVE' OR s.updated_at >= CAST(:date AS TIMESTAMP))
            """, nativeQuery = true)
    long countActiveAtDate(@Param("date") LocalDate date);

    // ==================== OVERDUE ====================

    @Query(value = """
            SELECT c.customer_id,
                   cu.name AS customer_name,
                   COUNT(*) AS overdue_count,
                   COALESCE(SUM(c.value), 0) AS total_overdue_value
            FROM charges c
            JOIN customers cu ON c.customer_id = cu.id
            WHERE c.status = 'OVERDUE'
            GROUP BY c.customer_id, cu.name
            ORDER BY total_overdue_value DESC
            """, nativeQuery = true)
    List<Object[]> findOverdueSummary();

    // ==================== DASHBOARD ====================

    @Query(value = """
            SELECT COUNT(*) FROM customers WHERE deleted_at IS NULL
            """, nativeQuery = true)
    long countActiveCustomers();

    @Query(value = """
            SELECT COUNT(*) FROM charges WHERE status = 'OVERDUE'
            """, nativeQuery = true)
    long countOverdueCharges();

    @Query(value = """
            SELECT COALESCE(SUM(value), 0) FROM charges WHERE status = 'OVERDUE'
            """, nativeQuery = true)
    BigDecimal sumOverdueValue();

    @Query(value = """
            SELECT COUNT(*) FROM charges
            WHERE status IN ('CONFIRMED', 'RECEIVED')
              AND due_date >= :from AND due_date <= :to
            """, nativeQuery = true)
    long countReceivedChargesInPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = """
            SELECT COALESCE(SUM(value), 0) FROM charges
            WHERE status IN ('CONFIRMED', 'RECEIVED')
              AND due_date >= :from AND due_date <= :to
            """, nativeQuery = true)
    BigDecimal sumRevenueInPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = """
            SELECT COUNT(*) FROM charges
            """, nativeQuery = true)
    long countAllCharges();
}
