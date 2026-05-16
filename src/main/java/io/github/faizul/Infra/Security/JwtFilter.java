package io.github.faizul.Infra.Security;

import com.google.common.net.HttpHeaders;
import io.github.faizul.Jwt.JwtService;
import io.github.faizul.Role.RoleRepository;
import io.github.faizul.User.UserRepository;
import io.github.faizul.UserRole.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
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
            return chain.filter(exchange);
        }

        String email = jwtService.extractEmail(token);

        return authenticate(email)
                .flatMap(authentication ->

                        chain.filter(exchange)
                                .contextWrite(
                                        ReactiveSecurityContextHolder.withAuthentication(authentication)
                                )
                )
                .onErrorResume(e -> chain.filter(exchange));
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
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .flatMap(user ->

                        userRoleRepository.findByUserId(user.getId())
                                .flatMap(userRole ->
                                        roleRepository.findById(userRole.getRoleId())
                                )
                                .map(role ->
                                        new SimpleGrantedAuthority(role.getName().name())
                                )
                                .collectList()
                                .map(authorities -> {

                                    UserDetails userDetails =
                                            new UserDetailImp(user,authorities);

                                    return new UsernamePasswordAuthenticationToken(
                                            userDetails,
                                            null,
                                            authorities
                                    );
                                })
                );
    }

}
