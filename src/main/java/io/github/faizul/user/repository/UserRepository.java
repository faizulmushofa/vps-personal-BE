package io.github.faizul.user.repository;


import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import io.github.faizul.user.model.User;

@Repository
public interface UserRepository extends ReactiveCrudRepository<User,Long> {
    Mono<Boolean> existsByEmail(String email);
    Mono<User> findByEmail(String email);
}
