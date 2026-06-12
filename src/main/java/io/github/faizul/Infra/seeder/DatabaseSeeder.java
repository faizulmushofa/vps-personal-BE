package io.github.faizul.infra.seeder;

import io.github.faizul.security.role.Role;
import io.github.faizul.security.role.RoleRepository;
import io.github.faizul.security.role.Roles;
import io.github.faizul.User.core.User;
import io.github.faizul.User.core.UserRepository;
import io.github.faizul.security.userrole.UserRole;
import io.github.faizul.security.userrole.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

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
        return Flux.just(Roles.ADMIN, Roles.USER)
                .flatMap(roleName -> roleRepository.findByName(roleName)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.info("Seeding role: {}...", roleName);
                            return roleRepository.save(Role.builder().name(roleName).build());
                        })))
                .then();
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
                                .isActive(true)
                                .build();

                        User user = User.builder()
                                .username("user")
                                .email("user@example.com")
                                .password(passwordEncoder.encode("password"))
                                .isActive(true)
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
                    storage_quota BIGINT DEFAULT 1073741824,
                    full_name VARCHAR(255),
                    avatar_url VARCHAR(1024),
                    phone_number VARCHAR(50),
                    is_active BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted_at TIMESTAMP
                );
                CREATE TABLE IF NOT EXISTS roles (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL UNIQUE
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
                    provider VARCHAR(50) DEFAULT 'STORAGE_NODE',
                    external_account_id BIGINT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (external_account_id) REFERENCES external_users(id) ON DELETE CASCADE
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
                    user_id BIGINT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP,
                    share_token VARCHAR(255) UNIQUE,
                    is_public BOOLEAN DEFAULT FALSE,
                    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    UNIQUE(file_id, user_id)
                );
                CREATE TABLE IF NOT EXISTS external_users (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT,
                    provider VARCHAR(255) NOT NULL,
                    provider_user_id VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL,
                    access_token TEXT,
                    refresh_token TEXT,
                    expires_at BIGINT,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                CREATE TABLE IF NOT EXISTS otp_verifications (
                    id BIGSERIAL PRIMARY KEY,
                    email VARCHAR(255) NOT NULL,
                    otp_code VARCHAR(6) NOT NULL,
                    type VARCHAR(50) NOT NULL,
                    expiry_time TIMESTAMP NOT NULL,
                    verified BOOLEAN DEFAULT FALSE
                );
                ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(255);
                ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(1024);
                ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number VARCHAR(50);
                ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;
                ALTER TABLE files ADD COLUMN IF NOT EXISTS provider VARCHAR(50) DEFAULT 'STORAGE_NODE';
                ALTER TABLE files ADD COLUMN IF NOT EXISTS external_account_id BIGINT;
                CREATE TABLE IF NOT EXISTS file_summaries (
                    id BIGSERIAL PRIMARY KEY,
                    file_id UUID NOT NULL UNIQUE,
                    summary TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
                );
                ALTER TABLE file_shared ALTER COLUMN user_id DROP NOT NULL;
                ALTER TABLE file_shared ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
                ALTER TABLE file_shared ADD COLUMN IF NOT EXISTS share_token VARCHAR(255) UNIQUE;
                ALTER TABLE file_shared ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT FALSE;
                """;
        log.info("Initializing database schema...");
        return Flux.fromArray(schema.split(";"))
                .map(String::trim)
                .filter(sql -> !sql.isEmpty())
                .concatMap(sql -> databaseClient.sql(sql).then())
                .then();
    }
}
