package io.github.faizul.security.filter;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CurrentUserContext {

    public Mono<UserDetailImpl> getUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getPrincipal())
                .cast(UserDetailImpl.class);
    }

    public Mono<Long> getUserId() {
        return getUser()
                .map(UserDetailImpl::getId);
    }

}
