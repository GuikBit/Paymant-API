package br.com.holding.payments.idempotency;

import br.com.holding.payments.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyService idempotencyService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || request.getHeader(IDEMPOTENCY_KEY_HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long companyId = TenantContext.getCompanyId();
        if (companyId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        String endpoint = request.getRequestURI();

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        // Read body by letting the chain process first, then check
        // But we need the body BEFORE to check idempotency.
        // Use a pre-read approach:
        wrappedRequest.getInputStream().readAllBytes(); // force caching
        String requestBody = new String(wrappedRequest.getContentAsByteArray(), StandardCharsets.UTF_8);

        // Check for existing idempotent response
        try {
            Optional<CachedResponse> cached = idempotencyService.checkIdempotency(
                    companyId, endpoint, idempotencyKey, requestBody);

            if (cached.isPresent()) {
                CachedResponse cachedResponse = cached.get();
                log.debug("Idempotency hit: company={}, endpoint={}, key={}", companyId, endpoint, idempotencyKey);
                response.setStatus(cachedResponse.getStatus());
                response.setContentType("application/json");
                response.getWriter().write(cachedResponse.getBody());
                return;
            }
        } catch (IdempotencyConflictException e) {
            response.setStatus(422);
            response.setContentType("application/json");
            response.getWriter().write("{\"type\":\"https://api.holding.com.br/errors/idempotency-conflict\","
                    + "\"title\":\"Idempotency Conflict\","
                    + "\"status\":422,"
                    + "\"detail\":\"" + e.getMessage() + "\"}");
            return;
        }

        // Execute the actual request
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(wrappedRequest, wrappedResponse);

        // Save response for future idempotent calls (only for successful responses)
        int status = wrappedResponse.getStatus();
        if (status >= 200 && status < 300) {
            String responseBody = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
            try {
                idempotencyService.saveResponse(companyId, endpoint, idempotencyKey, requestBody, status, responseBody);
            } catch (Exception e) {
                log.warn("Failed to save idempotency response: {}", e.getMessage());
            }
        }

        wrappedResponse.copyBodyToResponse();
    }
}
