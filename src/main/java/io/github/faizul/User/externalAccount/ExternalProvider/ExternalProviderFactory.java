package io.github.faizul.User.externalAccount.ExternalProvider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ExternalProviderFactory {

    private final Map<String,ExternalProvider> providers;

    public ExternalProviderFactory(List<ExternalProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(
                        p -> p.providerName().toUpperCase(),
                        p -> p
                ));
    }

    public ExternalProvider getProvider(String providerName) {
        ExternalProvider p = providers.get(providerName.toUpperCase());

        if (p  == null) {
            throw new IllegalArgumentException("Unsupported provider: " + providerName);
        }
        return p;
    }

}
