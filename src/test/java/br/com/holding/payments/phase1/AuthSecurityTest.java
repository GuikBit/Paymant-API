package br.com.holding.payments.phase1;

import br.com.holding.payments.AbstractIntegrationTest;
import br.com.holding.payments.auth.dto.LoginRequest;
import br.com.holding.payments.auth.dto.TokenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fase 1 - Autenticacao e Seguranca JWT")
class AuthSecurityTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Endpoint protegido sem token retorna 403")
    void protectedEndpoint_withoutToken_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/companies", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Endpoint publico /actuator/health retorna 200")
    void healthEndpoint_returns200() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Login com credenciais invalidas retorna 422")
    void login_withInvalidCredentials_returns422() {
        LoginRequest request = new LoginRequest("invalid@email.com", "wrongpassword");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("Login com credenciais validas retorna tokens JWT")
    void login_withValidCredentials_returnsTokens() {
        LoginRequest request = new LoginRequest("admin@holding.dev", "admin123");

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/login", request, TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("Endpoint protegido com token valido retorna 200")
    void protectedEndpoint_withValidToken_returns200() {
        // Login first
        LoginRequest loginRequest = new LoginRequest("admin@holding.dev", "admin123");
        TokenResponse tokens = restTemplate.postForEntity(
                "/api/v1/auth/login", loginRequest, TokenResponse.class).getBody();

        // Access protected endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/companies", HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
