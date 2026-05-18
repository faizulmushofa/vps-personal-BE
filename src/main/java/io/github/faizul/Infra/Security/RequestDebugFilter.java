package io.github.faizul.Infra.Security;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RequestDebugFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        System.out.println("Request masuk: " +
                exchange.getRequest().getURI()
                + "header : " + exchange.getRequest().getHeaders()

        );



        return chain.filter(exchange)
                .doFinally(signalType -> {
                    System.out.println("Request selesai");
                    System.out.println(exchange.getResponse().getHeaders());
                });
    }
}
