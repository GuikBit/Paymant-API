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
                        .title("Payment API - Multi-Tenant Asaas Integration")
                        .version("1.0.0")
                        .description("API de Pagamentos Multi-Tenant com Integração Asaas")
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
