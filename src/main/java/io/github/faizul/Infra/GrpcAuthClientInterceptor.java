package io.github.faizul.Infra;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GrpcAuthClientInterceptor implements ClientInterceptor {

    @Value("${spring.grpc.security.service-token}")
    private String serviceToken;

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        log.info("Intercepting gRPC call to method: {}", method.getFullMethodName());

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                log.debug("Injecting Authorization Bearer token into gRPC headers");
                Metadata.Key<String> authKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
                headers.put(authKey, "Bearer " + serviceToken);
                super.start(responseListener, headers);
            }
        };
    }
}
