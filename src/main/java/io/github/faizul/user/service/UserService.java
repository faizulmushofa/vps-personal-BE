package io.github.faizul.user.service;

import io.github.faizul.user.dtos.UserDto;
import io.github.faizul.user.dtos.UpdateProfileRequest;
import io.github.faizul.user.dtos.UpdatePasswordRequest;
import io.github.faizul.user.model.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserService {
    Mono<UserDto> createUser(User user);
    Mono<Void> deleteByID(Long id);
    Flux<UserDto> getAllUsers();
    Mono<User> checkAndApplyDowngrade(User user);
    Mono<UserDto> getUserById(Long id);
    Mono<UserDto> updateProfile(Long id, UpdateProfileRequest request, org.springframework.web.server.ServerWebExchange exchange);
    Mono<Void> updatePassword(Long id, UpdatePasswordRequest request, org.springframework.web.server.ServerWebExchange exchange);
}
