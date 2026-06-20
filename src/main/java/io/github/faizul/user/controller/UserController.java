package io.github.faizul.user.controller;

import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.user.dtos.UpdatePasswordRequest;
import io.github.faizul.user.dtos.UpdateProfileRequest;
import io.github.faizul.user.dtos.UserDto;
import io.github.faizul.user.model.SubscriptionRequest;
import io.github.faizul.user.model.User;
import io.github.faizul.user.service.SubscriptionRequestService;
import io.github.faizul.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;



@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUserContext currentUserContext;
    private final SubscriptionRequestService subscriptionRequestService;

    @PostMapping("/me/subscription-request")
    public Mono<ResponseEntity<SubscriptionRequest>> createSubscriptionRequest(@RequestParam String tier, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> subscriptionRequestService.createRequest(userId, tier, exchange))
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
                .flatMap(userId -> userService.updateProfile(userId, request, exchange))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/me/password")
    public Mono<ResponseEntity<Void>> updateMyPassword(@Valid @RequestBody UpdatePasswordRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(userId -> userService.updatePassword(userId, request, exchange))
                .then(Mono.just(ResponseEntity.ok().build()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<UserDto>> createUser(@RequestBody User user) {
        return userService.createUser(user)
                .map(userDto -> new ResponseEntity<>(userDto, HttpStatus.CREATED));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<UserDto> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or principal.id == #id")
    public Mono<ResponseEntity<UserDto>> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable Long id) {
        return userService.deleteByID(id)
                .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)));
    }
}
