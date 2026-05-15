package io.github.faizul.UserRole;

import io.github.faizul.Role.RoleRepository;
import io.github.faizul.User.UserRoleRepository;
import io.github.faizul.User.UserService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UserRoleService {

    private final UserService userService;
    private UserRoleRepository userRoleRepository;
    private RoleRepository roleRepository;

    public UserRoleService(UserService userService) {
        this.userService = userService;
    }

    public Mono<Void> assignDefaultRole(Long userId){
        return roleRepository.findByName("USER")
                .flatMap(role ->
                        userRoleRepository.save(
                               new UserRole(userId,role.getId())
                        )
                )
                .then();
    }

}
