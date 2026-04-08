package br.com.holding.payments.idempotency;

import br.com.holding.payments.company.Company;
import br.com.holding.payments.company.CompanyRepository;
import br.com.holding.payments.tenant.CrossTenant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
@Slf4j
public class IdempotencyService {

    private static final Duration REDIS_TTL = Duration.ofHours(24);
    private static final Duration POSTGRES_TTL = Duration.ofHours(24);

    private final IdempotencyKeyRepository repository;
    private final CompanyRepository companyRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter redisHitCounter;
    private final Counter postgresHitCounter;

    public IdempotencyService(IdempotencyKeyRepository repository,
                              CompanyRepository companyRepository,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisHitCounter = Counter.builder("idempotency_hit_total")
                .tag("layer", "redis")
                .description("Idempotency cache hits")
                .register(meterRegistry);
        this.postgresHitCounter = Counter.builder("idempotency_hit_total")
                .tag("layer", "postgres")
                .description("Idempotency cache hits")
                .register(meterRegistry);
    }

    public Optional<CachedResponse> checkIdempotency(Long companyId, String endpoint, String key, String requestBody) {
        String requestHash = hashBody(requestBody);

        // Layer 1: Redis fast-path
        Optional<CachedResponse> redisResult = checkRedis(companyId, endpoint, key, requestHash);
        if (redisResult.isPresent()) {
            redisHitCounter.increment();
            return redisResult;
        }

        // Layer 2: Postgres (source of truth)
        Optional<CachedResponse> pgResult = checkPostgres(companyId, endpoint, key, requestHash);
        if (pgResult.isPresent()) {
            postgresHitCounter.increment();
            // Repopulate Redis
            populateRedis(companyId, endpoint, key, pgResult.get());
        }

        return pgResult;
    }

    @Transactional
    public void saveResponse(Long companyId, String endpoint, String key, String requestBody,
                             int responseStatus, String responseBody) {
        String requestHash = hashBody(requestBody);

        Company company = companyRepository.getReferenceById(companyId);

        IdempotencyKey idempotencyKey = IdempotencyKey.builder()
                .company(company)
                .key(key)
                .endpoint(endpoint)
                .requestHash(requestHash)
                .responseStatus(responseStatus)
                .responseBody(responseBody)
                .expiresAt(LocalDateTime.now().plus(POSTGRES_TTL))
                .build();

        repository.save(idempotencyKey);

        CachedResponse cached = new CachedResponse(responseStatus, responseBody, requestHash);
        populateRedis(companyId, endpoint, key, cached);

        log.debug("Saved idempotency key: company={}, endpoint={}, key={}", companyId, endpoint, key);
    }

    @Transactional
    @CrossTenant(reason = "Cleanup expired keys from all tenants")
    public int cleanupExpired() {
        return repository.deleteExpired(LocalDateTime.now());
    }

    private Optional<CachedResponse> checkRedis(Long companyId, String endpoint, String key, String requestHash) {
        try {
            String redisKey = buildRedisKey(companyId, endpoint, key);
            String value = redisTemplate.opsForValue().get(redisKey);

            if (value == null) {
                return Optional.empty();
            }

            CachedResponse cached = objectMapper.readValue(value, CachedResponse.class);
            validateRequestHash(cached.getRequestHash(), requestHash, key);
            return Optional.of(cached);
        } catch (IdempotencyConflictException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis idempotency check failed, falling through to Postgres: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<CachedResponse> checkPostgres(Long companyId, String endpoint, String key, String requestHash) {
        return repository.findByCompanyIdAndEndpointAndKey(companyId, endpoint, key)
                .map(ik -> {
                    validateRequestHash(ik.getRequestHash(), requestHash, key);
                    return new CachedResponse(ik.getResponseStatus(), ik.getResponseBody(), ik.getRequestHash());
                });
    }

    private void populateRedis(Long companyId, String endpoint, String key, CachedResponse cached) {
        try {
            String redisKey = buildRedisKey(companyId, endpoint, key);
            String value = objectMapper.writeValueAsString(cached);
            redisTemplate.opsForValue().set(redisKey, value, REDIS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache idempotency response in Redis: {}", e.getMessage());
        }
    }

    private void validateRequestHash(String storedHash, String currentHash, String key) {
        if (storedHash != null && !storedHash.equals(currentHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency key '" + key + "' was already used with a different request body");
        }
    }

    private String buildRedisKey(Long companyId, String endpoint, String key) {
        return "idem:" + companyId + ":" + endpoint + ":" + key;
    }

    static String hashBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
