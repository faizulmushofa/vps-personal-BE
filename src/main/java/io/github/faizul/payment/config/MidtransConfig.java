package io.github.faizul.payment.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@Getter
public class MidtransConfig {

    @Value("${midtrans.server-key:dummy-midtrans-server-key}")
    private String serverKey;

    @Value("${midtrans.client-key:dummy-midtrans-client-key}")
    private String clientKey;

    @Value("${midtrans.snap-url:https://app.sandbox.midtrans.com/snap/v1}")
    private String snapUrl;

    @Value("${midtrans.api-url:https://api.sandbox.midtrans.com}")
    private String apiUrl;

    private String getAuthHeader() {
        return "Basic " + Base64.getEncoder().encodeToString(
                (serverKey + ":").getBytes(StandardCharsets.UTF_8)
        );
    }

    @Bean(name = "midtransSnapWebClient")
    public WebClient midtransSnapWebClient() {
        io.netty.resolver.DefaultAddressResolverGroup resolver = io.netty.resolver.DefaultAddressResolverGroup.INSTANCE;
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create().resolver(resolver);

        return WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .baseUrl(snapUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, getAuthHeader())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean(name = "midtransApiWebClient")
    public WebClient midtransApiWebClient() {
        io.netty.resolver.DefaultAddressResolverGroup resolver = io.netty.resolver.DefaultAddressResolverGroup.INSTANCE;
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create().resolver(resolver);

        return WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, getAuthHeader())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
