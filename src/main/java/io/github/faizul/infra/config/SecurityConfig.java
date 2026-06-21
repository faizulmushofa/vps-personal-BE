package io.github.faizul.infra.config;

import io.github.faizul.infra.config.*;
import io.github.faizul.security.filter.*;

import io.github.faizul.security.filter.JwtFilter;
import io.github.faizul.security.filter.RequestDebugFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authorization.HttpStatusServerAccessDeniedHandler;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final RequestDebugFilter requestDebugFilter;
    private final Environment environment;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    public PasswordEncoder passwordEncoder(){
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(frontendUrl));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "accept", "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"));
        configuration.setExposedHeaders(Arrays.asList("Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http){

        // OWASP A05 FIX: Only allow Swagger in dev/local profiles
        boolean isProduction = Arrays.asList(environment.getActiveProfiles()).contains("prod")
                || Arrays.asList(environment.getActiveProfiles()).contains("production");

        var authorizeSpec = http
                .cors(corsSpec -> corsSpec.configurationSource(corsConfigurationSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .exceptionHandling(exceptionHandlingSpec -> exceptionHandlingSpec
                        .authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(new HttpStatusServerAccessDeniedHandler(HttpStatus.FORBIDDEN))
                )
                .authorizeExchange( authorizeExchangeSpec -> {
                    authorizeExchangeSpec
                            .pathMatchers("/", "/index.html", "/styles.css", "/app.js", "/favicon.ico").permitAll()
                            .pathMatchers("/api/auth/academic/**").authenticated()
                            .pathMatchers("/api/auth/**").permitAll()
                            .pathMatchers("/api/reports").permitAll()
                            .pathMatchers("/api/files/share/public/**").permitAll()
                            .pathMatchers("/api/google-drive/share/public/**").permitAll()
                            .pathMatchers("/api/preview/public/**").permitAll()
                            .pathMatchers("/api/shared-folders/public/**").permitAll()
                            .pathMatchers("/api/subscriptions/payment/webhook").permitAll()
                            .pathMatchers("/admin/**").hasRole("ADMIN")
                            .pathMatchers("/api/admin/**").hasRole("ADMIN");

                    // Only expose Swagger endpoints in non-production environments
                    if (!isProduction) {
                        authorizeExchangeSpec
                                .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll();
                    } else {
                        authorizeExchangeSpec
                                .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").denyAll();
                    }

                    authorizeExchangeSpec.anyExchange().authenticated();
                })
                .addFilterAfter(requestDebugFilter,
                        SecurityWebFiltersOrder.AUTHENTICATION
                )
                .addFilterAt(
                        jwtFilter,
                        SecurityWebFiltersOrder.AUTHENTICATION
                );

        return authorizeSpec.build();
    }

}
