package io.github.faizul.infra.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("VPS Personal Backend API")
                        .description("Dokumentasi API untuk VPS Personal Backend yang berisi endpoint untuk Manajemen File, AI Assistant, Autentikasi Pengguna, dan fitur download/upload.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Faizul Mushofa")
                                .email("faizul.mushofa@example.com")
                                .url("https://github.com/faizul"))
                        .license(new License()
                                .name("Lisensi MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .addServersItem(new Server()
                        .url("http://localhost:8090")
                        .description("Server Lokal"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Masukkan Token JWT yang didapatkan dari /api/auth/login or /api/auth/register.")));
    }
}
