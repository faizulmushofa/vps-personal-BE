package io.github.faizul.security.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class RequestDebugFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        String device = userAgent != null ? userAgent : "Unknown Device";

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .defaultIfEmpty("Anonymous")
                .flatMap(username -> {
                    log.info("--> {} {} | User: {} | Device: {}", method, path, username, device);
                    return chain.filter(exchange)
                            .doOnSuccess(v -> {
                                long duration = System.currentTimeMillis() - startTime;
                                int statusCode = exchange.getResponse().getStatusCode() != null 
                                        ? exchange.getResponse().getStatusCode().value() 
                                        : 200;
                                log.info("<-- {} {} | User: {} | Status: {} | Duration: {}ms", 
                                        method, path, username, statusCode, duration);
                            })
                            .doOnError(err -> {
                                long duration = System.currentTimeMillis() - startTime;
                                int statusCode = exchange.getResponse().getStatusCode() != null 
                                        ? exchange.getResponse().getStatusCode().value() 
                                        : 500;
                                log.error("<-- {} {} | User: {} | Status: {} | Error: {} | Duration: {}ms", 
                                        method, path, username, statusCode, err.getMessage(), duration);
                            });
                });
    }
}
