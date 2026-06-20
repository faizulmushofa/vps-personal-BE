package io.github.faizul.user.service.impl;

import io.github.faizul.user.dtos.UserDto;
import io.github.faizul.user.dtos.UpdateProfileRequest;
import io.github.faizul.user.dtos.UpdatePasswordRequest;
import io.github.faizul.security.userrole.service.UserRoleService;
import io.github.faizul.security.userrole.repository.UserRoleRepository;
import io.github.faizul.security.role.repository.RoleRepository;
import io.github.faizul.storage.file.repository.FileRepository;
import io.github.faizul.storage.upload.service.UploadStorageService;
import io.github.faizul.user.repository.ExternalAccountRepository;
import io.github.faizul.security.jwt.EncryptionService;
import io.github.faizul.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import io.github.faizul.user.mapper.UserMapper;
import io.github.faizul.user.model.User;
import io.github.faizul.user.repository.UserRepository;

@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserRoleService userRoleService;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileRepository fileRepository;
    private final UploadStorageService uploadStorageService;
    private final ExternalAccountRepository externalAccountRepository;
    private final EncryptionService encryptionService;
    private final io.github.faizul.activity.service.UserActivityService userActivityService;

    @Override
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

    @Override
    public Mono<Void> deleteByID(Long id){
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Id Not Found")))
                .flatMap(user -> {
                    // 1. Delete physical files from storage node
                    Mono<Void> deletePhysicalFilesMono = fileRepository.findByUserId(id)
                            .filter(file -> "STORAGE_NODE".equalsIgnoreCase(file.getProvider()))
                            .flatMap(file -> uploadStorageService.deleteFile(id, file.getId().toString())
                                    .onErrorResume(e -> {
                                        System.err.println("Warning: Gagal menghapus file fisik di storage node: " + e.getMessage());
                                        return Mono.empty();
                                    }))
                            .then();

                    // 2. Revoke GDrive accounts
                    Mono<Void> revokeExternalAccountsMono = externalAccountRepository.findAllByUserId(id)
                            .flatMap(account -> {
                                if ("GOOGLE".equalsIgnoreCase(account.getProvider())) {
                                    String token = account.getRefreshToken() != null ? account.getRefreshToken() : account.getAccessToken();
                                    if (token != null && !token.isEmpty()) {
                                        String decryptedToken = encryptionService.decrypt(token);
                                        return org.springframework.web.reactive.function.client.WebClient.create()
                                                .post()
                                                .uri("https://oauth2.googleapis.com/revoke?token=" + decryptedToken)
                                                .header("Content-Type", "application/x-www-form-urlencoded")
                                                .retrieve()
                                                .toBodilessEntity()
                                                .onErrorResume(err -> {
                                                    System.err.println("Warning: Gagal merevoke token Google Drive: " + err.getMessage());
                                                    return Mono.empty();
                                                })
                                                .then();
                                    }
                                }
                                return Mono.empty();
                            })
                            .then();

                    return deletePhysicalFilesMono
                            .then(revokeExternalAccountsMono)
                            .then(userRepository.deleteById(id));
                });
    }

    @Override
    public Flux<UserDto> getAllUsers(){
        return this.userRepository.findAll()
                .flatMap(user -> userRoleRepository.findByUserId(user.getId())
                        .flatMap(userRole -> roleRepository.findById(userRole.getRoleId()))
                        .map(role -> role.getName().name())
                        .collectList()
                        .map(roles -> UserMapper.UserToDto(user, roles))
                );
    }

    @Override
    public Mono<User> checkAndApplyDowngrade(User user) {
        if (user.getSubscriptionExpiresAt() != null && user.getSubscriptionExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            user.setSubscriptionTier("FREEMIUM");
            user.setStorageQuota(1073741824L); // 1 GB
            user.setSubscriptionExpiresAt(null);
            user.setAiDailyLimit(5);
            user.setMigrationDailyLimit(3);
            user.setMigrationMaxFileSize(268435456L);
            return userRepository.save(user);
        }
        return Mono.just(user);
    }

    @Override
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

    @Override
    public Mono<UserDto> updateProfile(Long id, UpdateProfileRequest request, org.springframework.web.server.ServerWebExchange exchange) {
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
                )
                .flatMap(userDto -> userActivityService.log(id, "UPDATE_PROFILE", "Mengubah informasi profil pengguna", exchange)
                        .thenReturn(userDto));
    }

    @Override
    public Mono<Void> updatePassword(Long id, UpdatePasswordRequest request, org.springframework.web.server.ServerWebExchange exchange) {
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
                .flatMap(saved -> userActivityService.log(id, "UPDATE_PASSWORD", "Mengubah kata sandi akun", exchange))
                .then();
    }
}
