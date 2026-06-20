package io.github.faizul.security.userrole.service.impl;

import io.github.faizul.security.role.repository.RoleRepository;
import io.github.faizul.security.role.model.Roles;
import io.github.faizul.security.userrole.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import io.github.faizul.security.userrole.model.UserRole;
import io.github.faizul.security.userrole.repository.UserRoleRepository;

@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    @Override
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
