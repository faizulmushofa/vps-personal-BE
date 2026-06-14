package io.github.faizul.User.core;

import io.github.faizul.User.dtos.UserDto;
import io.github.faizul.User.dtos.UpdateProfileRequest;
import io.github.faizul.User.dtos.UpdatePasswordRequest;
import io.github.faizul.security.userrole.UserRoleService;
import io.github.faizul.security.userrole.UserRoleRepository;
import io.github.faizul.security.role.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserRoleService userRoleService;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public Mono<UserDto> createUser(User user){
        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);
        if (user.getStorageQuota() == null) {
            user.setStorageQuota(1073741824L);
        }

        return userRepository.existsByEmail(user.getEmail())
            .flatMap(exist -> {
                if (exist){
                    return Mono.error(new IllegalArgumentException("Email already exist"));
                }

                return this.userRepository.save(user)
                       .flatMap(saved ->
                               userRoleService.assignDefaultRole(saved.getId())
                                       .thenReturn(saved))
                       .map(saved -> UserMapper.UserToDto(saved, List.of("USER")));
            });
    }

    public Mono<Void> deleteByID(Long id){
        return userRepository.existsById(id)
                .flatMap(exist -> {
                    if (!exist){
                        return Mono.error(new NoSuchElementException("Id Not Found"));
                    }

                    return userRepository.deleteById(id);
                });
    }

    public Flux<UserDto> getAllUsers(){
        return this.userRepository.findAll()
                .flatMap(user -> userRoleRepository.findByUserId(user.getId())
                        .flatMap(userRole -> roleRepository.findById(userRole.getRoleId()))
                        .map(role -> role.getName().name())
                        .collectList()
                        .map(roles -> UserMapper.UserToDto(user, roles))
                );
    }

    public Mono<User> checkAndApplyDowngrade(User user) {
        if (user.getSubscriptionExpiresAt() != null && user.getSubscriptionExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            user.setSubscriptionTier("FREEMIUM");
            user.setStorageQuota(1073741824L); // 1 GB
            user.setSubscriptionExpiresAt(null);
            return userRepository.save(user);
        }
        return Mono.just(user);
    }

    public Mono<UserDto> getUserById(Long id){
        return this.userRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Id Not Found")))
                .flatMap(this::checkAndApplyDowngrade)
                .flatMap(user -> userRoleRepository.findByUserId(user.getId())
                        .flatMap(userRole -> roleRepository.findById(userRole.getRoleId()))
                        .map(role -> role.getName().name())
                        .collectList()
                        .map(roles -> UserMapper.UserToDto(user, roles))
                );
    }

    public Mono<UserDto> updateProfile(Long id, UpdateProfileRequest request) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("User Not Found")))
                .flatMap(user -> {
                    if (request.fullName() != null) {
                        user.setFullName(request.fullName());
                    }
                    if (request.phoneNumber() != null) {
                        user.setPhoneNumber(request.phoneNumber());
                    }
                    if (request.avatarUrl() != null) {
                        user.setAvatarUrl(request.avatarUrl());
                    }
                    return userRepository.save(user);
                })
                .flatMap(saved -> userRoleRepository.findByUserId(saved.getId())
                        .flatMap(userRole -> roleRepository.findById(userRole.getRoleId()))
                        .map(role -> role.getName().name())
                        .collectList()
                        .map(roles -> UserMapper.UserToDto(saved, roles))
                );
    }

    public Mono<Void> updatePassword(Long id, UpdatePasswordRequest request) {
        if (request.newPassword() == null || request.newPassword().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Password baru tidak boleh kosong"));
        }
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("User Not Found")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
                        return Mono.error(new IllegalArgumentException("Password lama yang Anda masukkan salah"));
                    }
                    user.setPassword(passwordEncoder.encode(request.newPassword()));
                    return userRepository.save(user);
                })
                .then();
    }
}
