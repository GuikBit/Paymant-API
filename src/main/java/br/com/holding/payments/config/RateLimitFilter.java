package br.com.holding.payments.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter per IP address using a sliding window counter.
 * Resets every windowMs milliseconds.
 * Returns 429 Too Many Requests when limit is exceeded.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${app.rate-limit.requests-per-window:100}")
    private int maxRequestsPerWindow;

    @Value("${app.rate-limit.window-ms:60000}")
    private long windowMs;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = resolveClientKey(request);
        WindowCounter counter = counters.computeIfAbsent(clientKey, k -> new WindowCounter());

        if (counter.tryAcquire(maxRequestsPerWindow, windowMs)) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequestsPerWindow));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(
                    Math.max(0, maxRequestsPerWindow - counter.getCount())));
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for client: {}", clientKey);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again later.\"}");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
    }

    private String resolveClientKey(HttpServletRequest request) {
        // Use X-Company-Id header if available (tenant-aware rate limiting)
        String companyId = request.getHeader("X-Company-Id");
        String ip = getClientIp(request);
        return companyId != null ? "company:" + companyId : "ip:" + ip;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        boolean tryAcquire(int maxRequests, long windowMs) {
            long now = System.currentTimeMillis();
            if (now - windowStart > windowMs) {
                synchronized (this) {
                    if (now - windowStart > windowMs) {
                        count.set(0);
                        windowStart = now;
                    }
                }
            }
            return count.incrementAndGet() <= maxRequests;
        }

        int getCount() {
            return count.get();
        }
    }
}
