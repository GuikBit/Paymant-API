package br.com.holding.payments.apikey;

import br.com.holding.payments.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKeyValue = request.getHeader(API_KEY_HEADER);

        boolean authenticatedByApiKey = false;

        if (apiKeyValue != null && !apiKeyValue.isBlank()) {
            Optional<ApiKey> optKey = apiKeyService.authenticate(apiKeyValue);

            if (optKey.isPresent()) {
                ApiKey apiKey = optKey.get();

                var authorities = apiKey.getRoleSet().stream()
                        .map(role -> new SimpleGrantedAuthority(role.name()))
                        .toList();

                String principal = "apikey:" + apiKey.getName();

                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
                TenantContext.setCompanyId(apiKey.getCompany().getId());
                authenticatedByApiKey = true;

                log.debug("Autenticado via API Key: name={}, company_id={}",
                        apiKey.getName(), apiKey.getCompany().getId());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (authenticatedByApiKey) {
                TenantContext.clear();
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}
