package io.github.faizul.security.filter;

import com.google.common.net.HttpHeaders;
import io.github.faizul.security.jwt.JwtService;
import io.github.faizul.security.role.RoleRepository;
import io.github.faizul.User.UserRepository;
import io.github.faizul.security.userrole.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter implements WebFilter {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String token = resolveToken(exchange);

        if (token == null) {
            return chain.filter(exchange);
        }

        if (!jwtService.isValid(token)) {
            exchange.getResponse().getHeaders().add("X-Auth-Debug", "invalid-token");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String email = jwtService.extractEmail(token);

        return authenticate(email)
                .onErrorResume(e -> {
                    log.warn("JWT authentication failed: {}", e.getMessage());
                    exchange.getResponse().getHeaders().add("X-Auth-Debug", "authentication-error");
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return Mono.empty();
                })
                .flatMap(authentication ->
                        chain.filter(exchange)
                                .contextWrite(
                                        ReactiveSecurityContextHolder.withAuthentication(authentication)
                                )
                );
    }

    private String resolveToken(ServerWebExchange exchange) {

        String header = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        return null;
    }

    private Mono<Authentication> authenticate(String email) {

        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new BadCredentialsException("User not found")))
                .flatMap(user -> userRoleRepository.findByUserId(user.getId())
                        .flatMap(userRole -> roleRepository.findById(userRole.getRoleId()))
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                        .collectList()
                        .onErrorResume(e -> {
                            log.warn("Role lookup failed for user {}: {}", user.getId(), e.getMessage());
                            return Mono.just(List.of(new SimpleGrantedAuthority("ROLE_USER")));
                        })
                        .map(authorities -> {
                            List<SimpleGrantedAuthority> effectiveAuthorities = authorities.isEmpty()
                                    ? List.of(new SimpleGrantedAuthority("ROLE_USER"))
                                    : authorities;

                            UserDetails userDetails = new UserDetailImp(user, effectiveAuthorities);

                            return (Authentication) new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    effectiveAuthorities
                            );
                        }));
    }

}
