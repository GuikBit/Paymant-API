package br.com.holding.payments.config;

import br.com.holding.payments.auth.Role;
import br.com.holding.payments.auth.UserEntity;
import br.com.holding.payments.auth.UserRepository;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@Profile({"prod", "staging"})
@EnableConfigurationProperties(ProdMasterSeeder.MasterSeedProperties.class)
@RequiredArgsConstructor
@Slf4j
public class ProdMasterSeeder {

    private final PasswordEncoder passwordEncoder;
    private final MasterSeedProperties props;

    @Bean
    public CommandLineRunner seedMasterUser(CompanyRepository companyRepository,
                                            UserRepository userRepository) {
        return args -> seed(companyRepository, userRepository);
    }

    @Transactional
    public void seed(CompanyRepository companyRepository, UserRepository userRepository) {
        if (!props.isEnabled()) {
            log.info("Master seed disabled (app.master-seed.enabled=false). Skipping.");
            return;
        }

        if (isBlank(props.getEmail()) || isBlank(props.getPassword())) {
            log.warn("Master seed enabled but email or password missing. Skipping. " +
                    "Set MASTER_SEED_EMAIL and MASTER_SEED_PASSWORD env vars.");
            return;
        }

        if (userRepository.existsByEmail(props.getEmail())) {
            log.info("Master user '{}' already exists. Skipping seed.", props.getEmail());
            return;
        }

        Company holding = companyRepository.findAll().stream()
                .filter(c -> props.getCompanyCnpj().equals(c.getCnpj()))
                .findFirst()
                .orElseGet(() -> {
                    log.info("Holding company '{}' not found. Creating it.", props.getCompanyCnpj());
                    Company c = Company.builder()
                            .cnpj(props.getCompanyCnpj())
                            .razaoSocial(props.getCompanyRazaoSocial())
                            .nomeFantasia(props.getCompanyNomeFantasia())
                            .email(props.getEmail())
                            .build();
                    return companyRepository.save(c);
                });
        companyRepository.flush();

        UserEntity master = UserEntity.builder()
                .company(holding)
                .email(props.getEmail())
                .passwordHash(passwordEncoder.encode(props.getPassword()))
                .name(props.getName())
                .roles(Role.ROLE_HOLDING_ADMIN.name())
                .active(true)
                .build();
        userRepository.save(master);

        log.info("==============================================");
        log.info("  MASTER USER CREATED");
        log.info("  Email:   {}", props.getEmail());
        log.info("  Role:    {}", Role.ROLE_HOLDING_ADMIN.name());
        log.info("  Company: {} (id={})", holding.getNomeFantasia(), holding.getId());
        log.info("  IMPORTANT: disable app.master-seed.enabled after first boot.");
        log.info("==============================================");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @ConfigurationProperties(prefix = "app.master-seed")
    @lombok.Getter
    @lombok.Setter
    public static class MasterSeedProperties {
        private boolean enabled = false;
        private String email;
        private String password;
        private String name = "Master Admin";
        private String companyCnpj = "00000000000100";
        private String companyRazaoSocial = "Holding Master LTDA";
        private String companyNomeFantasia = "Holding Master";
    }
}
