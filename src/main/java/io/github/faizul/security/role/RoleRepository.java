package io.github.faizul.security.role;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface RoleRepository extends R2dbcRepository<Role,Long> {
    Mono<Role> findByName(Roles name);
}
