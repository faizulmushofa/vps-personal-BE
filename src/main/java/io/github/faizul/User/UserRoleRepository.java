package io.github.faizul.User;

import io.github.faizul.UserRole.UserRole;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface UserRoleRepository extends ReactiveCrudRepository<UserRole,Long> {
}
