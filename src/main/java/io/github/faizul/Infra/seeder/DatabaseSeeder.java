package io.github.faizul.infra.seeder;

import io.github.faizul.infra.seeder.*;
import io.github.faizul.User.*;
import io.github.faizul.security.userrole.*;
import io.github.faizul.security.role.*;

import io.github.faizul.security.role.Role;
import io.github.faizul.security.role.RoleRepository;
import io.github.faizul.security.role.Roles;
import io.github.faizul.User.User;
import io.github.faizul.User.UserRepository;
import io.github.faizul.security.userrole.UserRole;
import io.github.faizul.security.userrole.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseClient databaseClient;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting database seeder...");
        createSchema()
                .then(seedRoles())
                .then(seedUsers())
                .subscribe(
                        success -> log.info("Successfully seeded users and roles!"),
                        error -> log.error("Error seeding database: {}", error.getMessage()),
                        () -> log.info("Database seeding completed.")
                );
    }

    private Mono<Void> seedRoles() {
        return roleRepository.count()
                .flatMap(count -> {
                    if (count == 0) {
                        log.info("Seeding roles (ADMIN, USER)...");
                        Role adminRole = Role.builder().name(Roles.ADMIN).build();
                        Role userRole = Role.builder().name(Roles.USER).build();
                        return roleRepository.save(adminRole)
                                .then(roleRepository.save(userRole));
                    }
                    return Mono.empty();
                }).then();
    }

    private Mono<Void> seedUsers() {
        return userRepository.count()
                .flatMap(count -> {
                    if (count == 0) {
                        log.info("Seeding users (Admin and Regular User)...");
                        User admin = User.builder()
                                .username("admin")
                                .email("admin@example.com")
                                .password(passwordEncoder.encode("password"))
                                .build();

                        User user = User.builder()
                                .username("user")
                                .email("user@example.com")
                                .password(passwordEncoder.encode("password"))
                                .build();

                        return userRepository.save(admin)
                                .zipWith(roleRepository.findByName(Roles.ADMIN))
                                .flatMap(tuple -> userRoleRepository.save(
                                        UserRole.builder()
                                                .userId(tuple.getT1().getId())
                                                .roleId(tuple.getT2().getId())
                                                .build()
                                ))
                                .then(userRepository.save(user))
                                .zipWith(roleRepository.findByName(Roles.USER))
                                .flatMap(tuple -> userRoleRepository.save(
                                        UserRole.builder()
                                                .userId(tuple.getT1().getId())
                                                .roleId(tuple.getT2().getId())
                                                .build()
                                ));
                    }
                    return Mono.empty();
                }).then();
    }

    private Mono<Void> createSchema() {
        String schema = """
                CREATE TABLE IF NOT EXISTS users (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    storage_quota BIGINT DEFAULT 5368709120,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted_at TIMESTAMP
                );
                CREATE TABLE IF NOT EXISTS roles (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL
                );
                CREATE TABLE IF NOT EXISTS user_roles (
                    user_id BIGINT NOT NULL,
                    role_id BIGINT NOT NULL,
                    PRIMARY KEY (user_id, role_id),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
                );
                CREATE TABLE IF NOT EXISTS refresh_token (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    token VARCHAR(255) NOT NULL,
                    revoked BOOLEAN DEFAULT FALSE,
                    expired_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                CREATE TABLE IF NOT EXISTS files (
                    id UUID PRIMARY KEY,
                    original_file_name VARCHAR(255) NOT NULL,
                    storage_name VARCHAR(255) NOT NULL,
                    size BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                CREATE TABLE IF NOT EXISTS upload_sessions (
                    id UUID PRIMARY KEY,
                    file_id UUID NOT NULL,
                    temp_path VARCHAR(1024) NOT NULL,
                    total_chunks INTEGER NOT NULL,
                    uploaded_chunks INTEGER NOT NULL,
                    status VARCHAR(64) NOT NULL,
                    completed_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
                );
                CREATE TABLE IF NOT EXISTS download_sessions (
                    id UUID PRIMARY KEY,
                    file_id UUID NOT NULL,
                    user_id BIGINT NOT NULL,
                    status VARCHAR(64) NOT NULL,
                    total_bytes BIGINT NOT NULL,
                    bytes_sent BIGINT NOT NULL,
                    started_at TIMESTAMP,
                    completed_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                CREATE TABLE IF NOT EXISTS file_shared (
                    id BIGSERIAL PRIMARY KEY,
                    file_id UUID NOT NULL,
                    user_id BIGINT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    UNIQUE(file_id, user_id)
                );
                """;
        log.info("Initializing database schema...");
        return databaseClient.sql(schema).then();
    }
}
