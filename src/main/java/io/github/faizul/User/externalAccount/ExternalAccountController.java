package io.github.faizul.User.externalAccount;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/external-accounts")
@RequiredArgsConstructor
public class ExternalAccountController {

    private final ExternalAccountService externalUserService;

    @GetMapping("/auth-url")
    public Mono<String> getAuthUrl(@RequestParam String provider) {
        return externalUserService.getAuthUrl(provider);
    }

    @PostMapping("/init")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Void> initAccount(@RequestParam String provider, @RequestBody Map<String, String> body, org.springframework.web.server.ServerWebExchange exchange) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return Mono.error(new IllegalArgumentException("Token is required"));
        }
        return externalUserService.handleCallback(provider, token, exchange);
    }

    @GetMapping("/me")
    public Flux<io.github.faizul.User.dtos.ExternalAccountDto> getMyAccounts() {
        return externalUserService.getMyAccounts();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> disconnect(@PathVariable Long id, org.springframework.web.server.ServerWebExchange exchange) {
        return externalUserService.disconnect(id, exchange);
    }
}
