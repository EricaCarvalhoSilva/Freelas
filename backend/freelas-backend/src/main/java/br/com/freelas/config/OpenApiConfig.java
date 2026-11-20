package br.com.freelas.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentação em /swagger-ui.html, com o botão "Authorize" para colar o token. */
@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI freelasOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Freelas API").version("1.0")
                        .description("API REST do Freelas (MeuFreela): empresas, freelancers, vagas, contratações, pagamentos em custódia, avaliações e substituição automática."))
                .components(new Components().addSecuritySchemes("token",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("token"));
    }
}
