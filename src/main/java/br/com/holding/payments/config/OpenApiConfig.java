package br.com.holding.payments.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Pagamentos - Integracao Multi-Tenant com Asaas")
                        .version("1.0.0")
                        .description("API de gestao de pagamentos multi-tenant com integracao ao gateway Asaas. " +
                                "Suporta multiplas empresas (tenants) com isolamento de dados via Row-Level Security, " +
                                "idempotencia em requisicoes, outbox pattern para eventos de dominio confiaveis, " +
                                "e auditoria completa de operacoes. " +
                                "Para autenticar, utilize o endpoint POST /api/v1/auth/login e envie o token JWT " +
                                "retornado no header Authorization com o prefixo Bearer.")
                        .contact(new Contact()
                                .name("Holding Payments Team")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer JWT"))
                .schemaRequirement("Bearer JWT", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token obtido via /api/v1/auth/login"));
    }
}
