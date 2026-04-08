package br.com.holding.payments.config;

import br.com.holding.payments.auth.Role;
import br.com.holding.payments.auth.UserEntity;
import br.com.holding.payments.auth.UserRepository;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder {

    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Bean
    public CommandLineRunner seedDevData(CompanyRepository companyRepository,
                                         UserRepository userRepository) {
        return args -> seed(companyRepository, userRepository);
    }

    @Transactional
    public void seed(CompanyRepository companyRepository, UserRepository userRepository) {
        if (companyRepository.count() > 0) {
            log.info("Dev data already exists, skipping seed.");
            return;
        }

        log.info("Seeding dev data...");

        // Create holding company (companies table has no RLS)
        Company holding = Company.builder()
                .cnpj("00000000000100")
                .razaoSocial("Holding Dev LTDA")
                .nomeFantasia("Holding Dev")
                .email("admin@holding.dev")
                .build();
        holding = companyRepository.save(holding);
        companyRepository.flush();

        // Set tenant context for RLS before inserting into users table
        entityManager.createNativeQuery("SET LOCAL app.current_company_id = '" + holding.getId() + "'")
                .executeUpdate();

        // Create admin user
        UserEntity admin = UserEntity.builder()
                .company(holding)
                .email("admin@holding.dev")
                .passwordHash(passwordEncoder.encode("admin123"))
                .name("Admin Dev")
                .roles(Role.ROLE_HOLDING_ADMIN.name())
                .active(true)
                .build();
        userRepository.save(admin);

        log.info("==============================================");
        log.info("  DEV SEED DATA CREATED");
        log.info("  Email:    admin@holding.dev");
        log.info("  Password: admin123");
        log.info("  Company:  {} (id={})", holding.getNomeFantasia(), holding.getId());
        log.info("==============================================");
    }
}
