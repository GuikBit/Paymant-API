package br.com.holding.payments.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Pagamentos - Integracao Multi-Tenant com Asaas")
                        .version("1.0.0")
                        .description("""
                                API de gestao de pagamentos multi-tenant com integracao ao gateway Asaas.

                                ## Funcionalidades principais
                                - **Multi-tenancy**: isolamento de dados via Row-Level Security (PostgreSQL)
                                - **Pagamentos**: PIX, Boleto, Cartao de Credito, Parcelamentos
                                - **Assinaturas**: lifecycle completo com mudanca de plano e pro-rata
                                - **Webhooks**: processamento tolerante a desordem com retry e DLQ
                                - **Idempotencia**: header `Idempotency-Key` em todos os POSTs criticos
                                - **Outbox Pattern**: eventos de dominio confiaveis para integracao externa
                                - **Auditoria**: log estruturado de toda operacao sensivel
                                - **Relatorios**: revenue, MRR, churn, inadimplencia com export CSV

                                ## Autenticacao
                                1. `POST /api/v1/auth/login` com email e senha
                                2. Use o `accessToken` retornado no header `Authorization: Bearer {token}`
                                3. Renove com `POST /api/v1/auth/refresh` antes de expirar

                                ## Multi-tenancy
                                Envie o header `X-Company-Id` em todas as requisicoes (exceto auth).
                                O RLS garante isolamento automatico dos dados por empresa.

                                ## Rate Limiting
                                Limite de 100 requisicoes por minuto por IP/tenant.
                                Headers de resposta: `X-RateLimit-Limit`, `X-RateLimit-Remaining`.
                                """)
                        .contact(new Contact()
                                .name("Holding Payments Team"))
                        .license(new License()
                                .name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("https://api-staging.holding.com.br").description("Staging"),
                        new Server().url("https://api.holding.com.br").description("Production")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer JWT"))
                .schemaRequirement("Bearer JWT", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token obtido via POST /api/v1/auth/login"));
    }
}
