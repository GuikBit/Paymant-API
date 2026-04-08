package br.com.holding.payments.company;

import br.com.holding.payments.audit.Auditable;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final EncryptionService encryptionService;

    @Transactional
    @Auditable(action = "COMPANY_CREATE", entity = "Company")
    public CompanyResponse create(CreateCompanyRequest request) {
        if (companyRepository.existsByCnpj(request.cnpj())) {
            throw new BusinessException("CNPJ already registered: " + request.cnpj());
        }

        Company company = Company.builder()
                .cnpj(request.cnpj())
                .razaoSocial(request.razaoSocial())
                .nomeFantasia(request.nomeFantasia())
                .email(request.email())
                .phone(request.phone())
                .asaasEnv(request.asaasEnv() != null ? request.asaasEnv() : AsaasEnvironment.SANDBOX)
                .planChangePolicy(request.planChangePolicy() != null ? request.planChangePolicy() : PlanChangePolicy.IMMEDIATE_PRORATA)
                .downgradeValidationStrategy(request.downgradeValidationStrategy() != null ? request.downgradeValidationStrategy() : DowngradeValidationStrategy.BLOCK)
                .gracePeriodDays(request.gracePeriodDays() != null ? request.gracePeriodDays() : 0)
                .build();

        if (request.asaasApiKey() != null && !request.asaasApiKey().isBlank()) {
            company.setAsaasApiKeyEncrypted(encryptionService.encrypt(request.asaasApiKey()));
        }

        company = companyRepository.save(company);
        return companyMapper.toResponse(company);
    }

    @Transactional(readOnly = true)
    public Page<CompanyResponse> findAll(Pageable pageable) {
        return companyRepository.findAll(pageable).map(companyMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CompanyResponse findById(Long id) {
        Company company = getCompanyOrThrow(id);
        return companyMapper.toResponse(company);
    }

    @Transactional
    @Auditable(action = "COMPANY_UPDATE", entity = "Company")
    public CompanyResponse update(Long id, UpdateCompanyRequest request) {
        Company company = getCompanyOrThrow(id);

        if (request.razaoSocial() != null) company.setRazaoSocial(request.razaoSocial());
        if (request.nomeFantasia() != null) company.setNomeFantasia(request.nomeFantasia());
        if (request.email() != null) company.setEmail(request.email());
        if (request.phone() != null) company.setPhone(request.phone());
        if (request.status() != null) company.setStatus(request.status());
        if (request.planChangePolicy() != null) company.setPlanChangePolicy(request.planChangePolicy());
        if (request.downgradeValidationStrategy() != null) company.setDowngradeValidationStrategy(request.downgradeValidationStrategy());
        if (request.gracePeriodDays() != null) company.setGracePeriodDays(request.gracePeriodDays());

        company = companyRepository.save(company);
        return companyMapper.toResponse(company);
    }

    @Transactional
    @Auditable(action = "COMPANY_UPDATE_CREDENTIALS", entity = "Company")
    public void updateCredentials(Long id, UpdateCredentialsRequest request) {
        Company company = getCompanyOrThrow(id);
        company.setAsaasApiKeyEncrypted(encryptionService.encrypt(request.asaasApiKey()));
        company.setAsaasEnv(request.asaasEnv());
        companyRepository.save(company);
        log.info("Asaas credentials updated for company {}", id);
    }

    public boolean testConnection(Long id) {
        Company company = getCompanyOrThrow(id);
        String apiKey = encryptionService.decrypt(company.getAsaasApiKeyEncrypted());

        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("No Asaas API key configured for company " + id);
        }

        String baseUrl = company.getAsaasEnv() == AsaasEnvironment.PRODUCTION
                ? "https://api.asaas.com/v3"
                : "https://sandbox.asaas.com/api/v3";

        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("access_token", apiKey)
                    .build();

            restClient.get()
                    .uri("/myAccount")
                    .retrieve()
                    .body(String.class);

            log.info("Asaas connection test successful for company {}", id);
            return true;
        } catch (Exception e) {
            log.warn("Asaas connection test failed for company {}: {}", id, e.getMessage());
            return false;
        }
    }

    /**
     * Returns the decrypted Asaas API key for the given company.
     * Used internally by the Asaas integration layer.
     */
    public String getDecryptedApiKey(Long companyId) {
        Company company = getCompanyOrThrow(companyId);
        return encryptionService.decrypt(company.getAsaasApiKeyEncrypted());
    }

    public AsaasEnvironment getAsaasEnvironment(Long companyId) {
        Company company = getCompanyOrThrow(companyId);
        return company.getAsaasEnv();
    }

    private Company getCompanyOrThrow(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", id));
    }
}
