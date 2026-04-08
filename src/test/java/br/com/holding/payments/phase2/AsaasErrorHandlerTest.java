package br.com.holding.payments.phase2;

import br.com.holding.payments.integration.asaas.client.AsaasApiException;
import br.com.holding.payments.integration.asaas.client.AsaasErrorHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

@WireMockTest
@DisplayName("Fase 2 - Tratamento de erros do Asaas")
class AsaasErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestClient createClient(WireMockRuntimeInfo wmRuntimeInfo) {
        AsaasErrorHandler errorHandler = new AsaasErrorHandler(objectMapper);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(wmRuntimeInfo.getHttpBaseUrl());
        return errorHandler.configure(builder).build();
    }

    @Test
    @DisplayName("Resposta 200 nao dispara erro")
    void success200_shouldNotThrow(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get("/test").willReturn(okJson("{\"id\":\"123\"}")));

        RestClient client = createClient(wmRuntimeInfo);
        String result = client.get().uri("/test").retrieve().body(String.class);

        assertThat(result).contains("123");
    }

    @Test
    @DisplayName("Erro 400 do Asaas eh parseado com detalhes")
    void error400_shouldParseAsaasErrors(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(post("/payments").willReturn(
                aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"errors\":[{\"code\":\"invalid_value\",\"description\":\"O campo value deve ser maior que zero\"}]}")
        ));

        RestClient client = createClient(wmRuntimeInfo);

        assertThatThrownBy(() ->
                client.post().uri("/payments").body("{}").retrieve().body(String.class)
        )
                .isInstanceOf(AsaasApiException.class)
                .satisfies(ex -> {
                    AsaasApiException asaasEx = (AsaasApiException) ex;
                    assertThat(asaasEx.getStatusCode()).isEqualTo(400);
                    assertThat(asaasEx.isClientError()).isTrue();
                    assertThat(asaasEx.getErrors()).hasSize(1);
                    assertThat(asaasEx.getErrors().get(0).code()).isEqualTo("invalid_value");
                });
    }

    @Test
    @DisplayName("Erro 404 do Asaas eh tratado como client error")
    void error404_shouldBeClientError(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get("/customers/cus_invalid").willReturn(
                aResponse().withStatus(404).withBody("{\"errors\":[{\"code\":\"not_found\",\"description\":\"Customer not found\"}]}")
        ));

        RestClient client = createClient(wmRuntimeInfo);

        assertThatThrownBy(() ->
                client.get().uri("/customers/cus_invalid").retrieve().body(String.class)
        )
                .isInstanceOf(AsaasApiException.class)
                .satisfies(ex -> {
                    AsaasApiException asaasEx = (AsaasApiException) ex;
                    assertThat(asaasEx.getStatusCode()).isEqualTo(404);
                    assertThat(asaasEx.isClientError()).isTrue();
                    assertThat(asaasEx.isServerError()).isFalse();
                });
    }

    @Test
    @DisplayName("Erro 500 do Asaas eh tratado como server error")
    void error500_shouldBeServerError(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get("/payments").willReturn(
                aResponse().withStatus(500).withBody("Internal Server Error")
        ));

        RestClient client = createClient(wmRuntimeInfo);

        assertThatThrownBy(() ->
                client.get().uri("/payments").retrieve().body(String.class)
        )
                .isInstanceOf(AsaasApiException.class)
                .satisfies(ex -> {
                    AsaasApiException asaasEx = (AsaasApiException) ex;
                    assertThat(asaasEx.getStatusCode()).isEqualTo(500);
                    assertThat(asaasEx.isServerError()).isTrue();
                    assertThat(asaasEx.isClientError()).isFalse();
                });
    }
}
