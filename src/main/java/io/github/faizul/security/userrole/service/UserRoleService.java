package io.github.faizul.security.userrole.service;

import reactor.core.publisher.Mono;

public interface UserRoleService {
    Mono<Void> assignDefaultRole(Long userId);
}
