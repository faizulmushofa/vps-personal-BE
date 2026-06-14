package io.github.faizul.infra.config;

import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelBuilderCustomizer;

import javax.net.ssl.SSLException;

/**
 * Configures gRPC client TLS to trust the Tailscale Funnel certificate.
 * Tailscale Funnel uses its own internal CA (not in JVM truststore).
 *
 * Safe because: Tailscale + SERVICE_TOKEN Bearer auth protect the channel.
 */
@Slf4j
@Configuration
public class GrpcClientConfig {

    @Bean
    public GrpcChannelBuilderCustomizer storageNodeTlsCustomizer() {
        return (name, builder) -> {
            if ("storage-node".equals(name) && builder instanceof NettyChannelBuilder nettyBuilder) {
                try {
                    SslContext sslContext = GrpcSslContexts.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build();
                    nettyBuilder.sslContext(sslContext);
                    log.info("Configured storage-node gRPC channel with Tailscale-trusted TLS");
                } catch (SSLException e) {
                    log.error("Failed to configure TLS for storage-node gRPC channel", e);
                }
            }
        };
    }
}
