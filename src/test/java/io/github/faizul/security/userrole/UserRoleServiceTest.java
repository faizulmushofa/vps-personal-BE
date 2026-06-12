package io.github.faizul.security.userrole;

import io.github.faizul.security.role.Role;
import io.github.faizul.security.role.RoleRepository;
import io.github.faizul.security.role.Roles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;

    @InjectMocks
    private UserRoleService userRoleService;

    @Test
    @DisplayName("should assign default USER role to a new user")
    void assignDefaultRole_success() {
        Role userRole = Role.builder().id(1L).name(Roles.USER).build();

        when(roleRepository.findByName(Roles.USER)).thenReturn(Mono.just(userRole));
        when(userRoleRepository.save(any(UserRole.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(userRoleService.assignDefaultRole(42L))
                .verifyComplete();

        verify(userRoleRepository).save(argThat(ur ->
                ur.getUserId().equals(42L) && ur.getRoleId().equals(1L)));
    }

    @Test
    @DisplayName("should propagate error when role not found")
    void assignDefaultRole_roleNotFound() {
        when(roleRepository.findByName(Roles.USER)).thenReturn(Mono.empty());

        StepVerifier.create(userRoleService.assignDefaultRole(42L))
                .verifyComplete();

        verify(userRoleRepository, never()).save(any());
    }
}
