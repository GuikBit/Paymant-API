package br.com.holding.payments.apikey;

import br.com.holding.payments.apikey.dto.ApiKeyResponse;
import br.com.holding.payments.apikey.dto.CreateApiKeyRequest;
import br.com.holding.payments.apikey.dto.CreateApiKeyResponse;
import br.com.holding.payments.common.errors.BusinessException;
import br.com.holding.payments.common.errors.ResourceNotFoundException;
import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.tenant.CrossTenant;
import br.com.holding.payments.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private static final String KEY_PREFIX = "pk_";
    private static final int KEY_LENGTH = 48;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public CreateApiKeyResponse create(CreateApiKeyRequest request) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.getReferenceById(companyId);

        String rawKey = generateRawKey();
        String keyHash = hashKey(rawKey);
        String prefix = rawKey.substring(0, Math.min(8, rawKey.length()));

        ApiKey apiKey = ApiKey.builder()
                .company(company)
                .keyPrefix(prefix)
                .keyHash(keyHash)
                .name(request.name())
                .description(request.description())
                .expiresAt(request.expiresAt())
                .build();
        apiKey.setRoleSet(request.roles());

        apiKey = apiKeyRepository.save(apiKey);

        log.info("API Key criada: id={}, name={}, company_id={}", apiKey.getId(), request.name(), companyId);

        return new CreateApiKeyResponse(toResponse(apiKey), rawKey);
    }

    @Transactional(readOnly = true)
    public Page<ApiKeyResponse> listByCompany(Pageable pageable) {
        Long companyId = TenantContext.getRequiredCompanyId();
        return apiKeyRepository.findByCompanyId(companyId, pageable).map(this::toResponse);
    }

    @Transactional
    public void revoke(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id));

        if (!apiKey.getActive()) {
            throw new BusinessException("API Key ja esta revogada");
        }

        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);

        log.info("API Key revogada: id={}, name={}", id, apiKey.getName());
    }

    @Transactional
    @CrossTenant(reason = "Autenticacao via API Key ocorre antes do TenantContext ser definido")
    public Optional<ApiKey> authenticate(String rawKey) {
        String keyHash = hashKey(rawKey);
        Optional<ApiKey> optKey = apiKeyRepository.findByKeyHashAndActiveTrue(keyHash);

        if (optKey.isPresent()) {
            ApiKey apiKey = optKey.get();
            if (apiKey.isExpired()) {
                return Optional.empty();
            }
            apiKeyRepository.updateLastUsedAt(apiKey.getId());
        }

        return optKey;
    }

    private String generateRawKey() {
        byte[] bytes = new byte[KEY_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private ApiKeyResponse toResponse(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getCompany().getId(),
                apiKey.getKeyPrefix(),
                apiKey.getName(),
                apiKey.getDescription(),
                apiKey.getRoleSet(),
                apiKey.getActive(),
                apiKey.getLastUsedAt(),
                apiKey.getExpiresAt(),
                apiKey.getCreatedAt()
        );
    }
}
