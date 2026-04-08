package br.com.holding.payments.plan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    @Query("SELECT MAX(p.version) FROM Plan p WHERE p.company.id = :companyId AND p.name = :name")
    Optional<Integer> findMaxVersionByCompanyAndName(@Param("companyId") Long companyId, @Param("name") String name);

    boolean existsByNameAndCompanyId(String name, Long companyId);

    @Query(value = "SELECT * FROM plans WHERE id = :id", nativeQuery = true)
    Optional<Plan> findByIdIncludingDeleted(@Param("id") Long id);
}
