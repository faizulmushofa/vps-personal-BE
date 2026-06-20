package io.github.faizul.security.userrole.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import io.github.faizul.security.userrole.model.UserRole;

public interface UserRoleRepository extends ReactiveCrudRepository<UserRole,Long> {
    Flux<UserRole> findByUserId(Long userId);
}
