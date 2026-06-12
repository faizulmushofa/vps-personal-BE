package io.github.faizul.User.core;

import io.github.faizul.User.dtos.UserDto;
import io.github.faizul.User.dtos.UpdateProfileRequest;
import io.github.faizul.User.dtos.UpdatePasswordRequest;
import io.github.faizul.security.filter.CurrentUserContext;
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

    @GetMapping("/me")
    public Mono<ResponseEntity<UserDto>> getMyProfile() {
        return currentUserContext.getUserId()
                .flatMap(userService::getUserById)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    public Mono<ResponseEntity<UserDto>> updateMyProfile(@RequestBody UpdateProfileRequest request) {
        return currentUserContext.getUserId()
                .flatMap(userId -> userService.updateProfile(userId, request))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/me/password")
    public Mono<ResponseEntity<Void>> updateMyPassword(@RequestBody UpdatePasswordRequest request) {
        return currentUserContext.getUserId()
                .flatMap(userId -> userService.updatePassword(userId, request))
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
