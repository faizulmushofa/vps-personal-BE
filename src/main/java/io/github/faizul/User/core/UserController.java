package io.github.faizul.User.core;

import io.github.faizul.User.dtos.UserDto;
import io.github.faizul.User.dtos.UpdateProfileRequest;
import io.github.faizul.User.dtos.UpdatePasswordRequest;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.User.subscription.SubscriptionRequest;
import io.github.faizul.User.subscription.SubscriptionRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUserContext currentUserContext;
    private final SubscriptionRequestService subscriptionRequestService;
    private final io.github.faizul.activity.UserActivityService userActivityService;

    @PostMapping("/me/subscription-request")
    public Mono<ResponseEntity<SubscriptionRequest>> createSubscriptionRequest(@RequestParam String tier, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> subscriptionRequestService.createRequest(userId, tier)
                        .flatMap(req -> userActivityService.log(userId, "CREATE_SUBSCRIPTION_REQUEST", "Mengajukan upgrade paket langganan ke tier: " + tier, exchange)
                                .thenReturn(req))
                )
                .map(req -> ResponseEntity.status(HttpStatus.CREATED).body(req));
    }

    @GetMapping("/me/subscription-request")
    public Mono<ResponseEntity<SubscriptionRequest>> getMySubscriptionRequest() {
        return currentUserContext.getUserId()
                .flatMap(subscriptionRequestService::getPendingRequest)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.noContent().build());
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<UserDto>> getMyProfile() {
        return currentUserContext.getUserId()
                .flatMap(userService::getUserById)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    public Mono<ResponseEntity<UserDto>> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> userService.updateProfile(userId, request)
                        .flatMap(userDto -> userActivityService.log(userId, "UPDATE_PROFILE", "Mengubah informasi profil pengguna", exchange)
                                .thenReturn(userDto))
                )
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/me/password")
    public Mono<ResponseEntity<Void>> updateMyPassword(@Valid @RequestBody UpdatePasswordRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> userService.updatePassword(userId, request)
                        .then(userActivityService.log(userId, "UPDATE_PASSWORD", "Mengubah kata sandi akun", exchange))
                )
                .then(Mono.just(ResponseEntity.ok().build()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ADMIN')")
    public Mono<ResponseEntity<UserDto>> createUser(@RequestBody User user) {
        return userService.createUser(user)
                .map(userDto -> new ResponseEntity<>(userDto, HttpStatus.CREATED));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public Flux<UserDto> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN') or principal.id == #id")
    public Mono<ResponseEntity<UserDto>> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ADMIN')")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable Long id) {
        return userService.deleteByID(id)
                .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }
}
