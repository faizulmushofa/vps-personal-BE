package io.github.faizul.UserRole;

import io.github.faizul.Role.RoleRepository;
import io.github.faizul.Role.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    public Mono<Void> assignDefaultRole(Long userId){
        return roleRepository.findByName(Roles.USER)
                .flatMap(role ->
                        userRoleRepository.save(
                                UserRole.builder()
                                        .userId(userId)
                                        .roleId(role.getId())
                                        .build()
                        )
                )
                .then();
    }
}
