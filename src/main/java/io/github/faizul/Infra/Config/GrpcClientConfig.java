package io.github.faizul.infra.config;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ssl.*;
import org.springframework.boot.autoconfigure.ssl.SslBundleRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Programmatically registers a custom SSL Bundle that trusts all certificates.
 * This is used for the gRPC storage-node client to trust the Tailscale Funnel SSL cert.
 *
 * Bypassing manual NettyChannelBuilder security configuration is required
 * to avoid IllegalStateException: "Cannot change security when using ChannelCredentials".
 */
@Slf4j
@Configuration
public class GrpcClientConfig {

    @Bean
    public SslBundleRegistrar trustAllSslBundleRegistrar() {
        return registry -> {
            log.info("Registering trust-all-bundle gRPC SSL Bundle");
            registry.registerBundle("trust-all-bundle", new SslBundle() {
                @Override
                public SslStoreBundle getStores() {
                    return SslStoreBundle.NONE;
                }

                @Override
                public SslBundleKey getKey() {
                    return SslBundleKey.NONE;
                }

                @Override
                public SslOptions getOptions() {
                    return SslOptions.NONE;
                }

                @Override
                public String getProtocol() {
                    return "TLS";
                }

                @Override
                public SslManagerBundle getManagers() {
                    try {
                        TrustManagerFactory tmf = InsecureTrustManagerFactory.INSTANCE;
                        // Use default KeyManagerFactory as fallback
                        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                        kmf.init(null, null);
                        return SslManagerBundle.of(kmf, tmf);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to configure SSL managers for trust-all-bundle", e);
                    }
                }

                @Override
                public SSLContext createSslContext() {
                    try {
                        SSLContext context = SSLContext.getInstance("TLS");
                        context.init(null, InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
                        return context;
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to create SSL Context for trust-all-bundle", e);
                    }
                }
            });
        };
    }
}
