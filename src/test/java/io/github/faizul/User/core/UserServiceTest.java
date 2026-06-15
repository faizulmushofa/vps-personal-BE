package io.github.faizul.User.core;

import io.github.faizul.User.dtos.UpdatePasswordRequest;
import io.github.faizul.User.dtos.UpdateProfileRequest;
import io.github.faizul.User.dtos.UserDto;
import io.github.faizul.security.role.Role;
import io.github.faizul.security.role.RoleRepository;
import io.github.faizul.security.role.Roles;
import io.github.faizul.security.userrole.UserRole;
import io.github.faizul.security.userrole.UserRoleRepository;
import io.github.faizul.security.userrole.UserRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleService userRoleService;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private io.github.faizul.File.core.FileRepository fileRepository;
    @Mock private io.github.faizul.Storage.upload.UploadStorageService uploadStorageService;
    @Mock private io.github.faizul.User.externalAccount.ExternalAccountRepository externalAccountRepository;
    @Mock private io.github.faizul.security.jwt.EncryptionService encryptionService;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private Role userRole;
    private UserRole sampleUserRole;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("hashedPassword")
                .fullName("Test User")
                .phoneNumber("08123456789")
                .storageQuota(1073741824L)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRole = Role.builder().id(1L).name(Roles.USER).build();
        sampleUserRole = UserRole.builder().userId(1L).roleId(1L).build();
    }

    @Nested
    @DisplayName("createUser")
    class CreateUserTests {

        @Test
        @DisplayName("should create user successfully when email does not exist")
        void createUser_success() {
            User newUser = User.builder()
                    .username("newuser")
                    .email("new@example.com")
                    .password("plainPassword")
                    .build();

            when(passwordEncoder.encode("plainPassword")).thenReturn("hashedPassword");
            when(userRepository.existsByEmail("new@example.com")).thenReturn(Mono.just(false));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(1L);
                return Mono.just(u);
            });
            when(userRoleService.assignDefaultRole(1L)).thenReturn(Mono.empty());

            StepVerifier.create(userService.createUser(newUser))
                    .assertNext(dto -> {
                        assertThat(dto.email()).isEqualTo("new@example.com");
                        assertThat(dto.username()).isEqualTo("newuser");
                        assertThat(dto.roles()).containsExactly("USER");
                    })
                    .verifyComplete();

            verify(passwordEncoder).encode("plainPassword");
            verify(userRepository).existsByEmail("new@example.com");
            verify(userRepository).save(any(User.class));
            verify(userRoleService).assignDefaultRole(1L);
        }

        @Test
        @DisplayName("should throw error when email already exists")
        void createUser_emailAlreadyExists() {
            User newUser = User.builder()
                    .username("newuser")
                    .email("existing@example.com")
                    .password("plainPassword")
                    .build();

            when(passwordEncoder.encode("plainPassword")).thenReturn("hashedPassword");
            when(userRepository.existsByEmail("existing@example.com")).thenReturn(Mono.just(true));

            StepVerifier.create(userService.createUser(newUser))
                    .expectErrorMatches(throwable ->
                            throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().equals("Email already exist"))
                    .verify();

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteByID")
    class DeleteByIdTests {

        @Test
        @DisplayName("should delete user when id exists")
        void deleteById_success() {
            when(userRepository.findById(1L)).thenReturn(Mono.just(sampleUser));
            when(fileRepository.findByUserId(1L)).thenReturn(Flux.empty());
            when(externalAccountRepository.findAllByUserId(1L)).thenReturn(Flux.empty());
            when(userRepository.deleteById(1L)).thenReturn(Mono.empty());

            StepVerifier.create(userService.deleteByID(1L))
                    .verifyComplete();

            verify(userRepository).deleteById(1L);
        }

        @Test
        @DisplayName("should throw error when id does not exist")
        void deleteById_notFound() {
            when(userRepository.findById(999L)).thenReturn(Mono.empty());

            StepVerifier.create(userService.deleteByID(999L))
                    .expectErrorMatches(throwable ->
                            throwable instanceof NoSuchElementException &&
                            throwable.getMessage().equals("Id Not Found"))
                    .verify();

            verify(userRepository, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("getAllUsers")
    class GetAllUsersTests {

        @Test
        @DisplayName("should return all users with their roles")
        void getAllUsers_success() {
            when(userRepository.findAll()).thenReturn(Flux.just(sampleUser));
            when(userRoleRepository.findByUserId(1L)).thenReturn(Flux.just(sampleUserRole));
            when(roleRepository.findById(1L)).thenReturn(Mono.just(userRole));

            StepVerifier.create(userService.getAllUsers())
                    .assertNext(dto -> {
                        assertThat(dto.id()).isEqualTo(1L);
                        assertThat(dto.email()).isEqualTo("test@example.com");
                        assertThat(dto.roles()).containsExactly("USER");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return empty flux when no users exist")
        void getAllUsers_empty() {
            when(userRepository.findAll()).thenReturn(Flux.empty());

            StepVerifier.create(userService.getAllUsers())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserByIdTests {

        @Test
        @DisplayName("should return user with roles when id exists")
        void getUserById_success() {
            when(userRepository.findById(1L)).thenReturn(Mono.just(sampleUser));
            when(userRoleRepository.findByUserId(1L)).thenReturn(Flux.just(sampleUserRole));
            when(roleRepository.findById(1L)).thenReturn(Mono.just(userRole));

            StepVerifier.create(userService.getUserById(1L))
                    .assertNext(dto -> {
                        assertThat(dto.id()).isEqualTo(1L);
                        assertThat(dto.username()).isEqualTo("testuser");
                        assertThat(dto.roles()).containsExactly("USER");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw error when id does not exist")
        void getUserById_notFound() {
            when(userRepository.findById(999L)).thenReturn(Mono.empty());

            StepVerifier.create(userService.getUserById(999L))
                    .expectErrorMatches(throwable ->
                            throwable instanceof NoSuchElementException &&
                            throwable.getMessage().equals("Id Not Found"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfileTests {

        @Test
        @DisplayName("should update profile fields that are provided")
        void updateProfile_success() {
            UpdateProfileRequest request = new UpdateProfileRequest(
                    "Updated Name", "08999999999", "https://avatar.url/new.png");

            when(userRepository.findById(1L)).thenReturn(Mono.just(sampleUser));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(sampleUser));
            when(userRoleRepository.findByUserId(1L)).thenReturn(Flux.just(sampleUserRole));
            when(roleRepository.findById(1L)).thenReturn(Mono.just(userRole));

            StepVerifier.create(userService.updateProfile(1L, request))
                    .assertNext(dto -> {
                        assertThat(dto.id()).isEqualTo(1L);
                    })
                    .verifyComplete();

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should only update non-null fields")
        void updateProfile_partialUpdate() {
            UpdateProfileRequest request = new UpdateProfileRequest("New Name", null, null);

            when(userRepository.findById(1L)).thenReturn(Mono.just(sampleUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(userRoleRepository.findByUserId(1L)).thenReturn(Flux.just(sampleUserRole));
            when(roleRepository.findById(1L)).thenReturn(Mono.just(userRole));

            StepVerifier.create(userService.updateProfile(1L, request))
                    .assertNext(dto -> {
                        assertThat(dto.fullName()).isEqualTo("New Name");
                        assertThat(dto.phoneNumber()).isEqualTo("08123456789");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw error when user not found")
        void updateProfile_userNotFound() {
            UpdateProfileRequest request = new UpdateProfileRequest("Name", null, null);
            when(userRepository.findById(999L)).thenReturn(Mono.empty());

            StepVerifier.create(userService.updateProfile(999L, request))
                    .expectErrorMatches(throwable ->
                            throwable instanceof NoSuchElementException &&
                            throwable.getMessage().equals("User Not Found"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("updatePassword")
    class UpdatePasswordTests {

        @Test
        @DisplayName("should update password when old password is correct")
        void updatePassword_success() {
            UpdatePasswordRequest request = new UpdatePasswordRequest("oldPass", "newPass123");

            when(userRepository.findById(1L)).thenReturn(Mono.just(sampleUser));
            when(passwordEncoder.matches("oldPass", "hashedPassword")).thenReturn(true);
            when(passwordEncoder.encode("newPass123")).thenReturn("newHashedPassword");
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(sampleUser));

            StepVerifier.create(userService.updatePassword(1L, request))
                    .verifyComplete();

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw error when old password is incorrect")
        void updatePassword_wrongOldPassword() {
            UpdatePasswordRequest request = new UpdatePasswordRequest("wrongOldPass", "newPass123");

            when(userRepository.findById(1L)).thenReturn(Mono.just(sampleUser));
            when(passwordEncoder.matches("wrongOldPass", "hashedPassword")).thenReturn(false);

            StepVerifier.create(userService.updatePassword(1L, request))
                    .expectErrorMatches(throwable ->
                            throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().equals("Password lama yang Anda masukkan salah"))
                    .verify();

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw error when new password is empty")
        void updatePassword_emptyNewPassword() {
            UpdatePasswordRequest request = new UpdatePasswordRequest("oldPass", "   ");

            StepVerifier.create(userService.updatePassword(1L, request))
                    .expectErrorMatches(throwable ->
                            throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().equals("Password baru tidak boleh kosong"))
                    .verify();
        }

        @Test
        @DisplayName("should throw error when new password is null")
        void updatePassword_nullNewPassword() {
            UpdatePasswordRequest request = new UpdatePasswordRequest("oldPass", null);

            StepVerifier.create(userService.updatePassword(1L, request))
                    .expectErrorMatches(throwable ->
                            throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().equals("Password baru tidak boleh kosong"))
                    .verify();
        }

        @Test
        @DisplayName("should throw error when user not found")
        void updatePassword_userNotFound() {
            UpdatePasswordRequest request = new UpdatePasswordRequest("oldPass", "newPass123");
            when(userRepository.findById(999L)).thenReturn(Mono.empty());

            StepVerifier.create(userService.updatePassword(999L, request))
                    .expectErrorMatches(throwable ->
                            throwable instanceof NoSuchElementException &&
                            throwable.getMessage().equals("User Not Found"))
                    .verify();
        }
    }
}
