package io.github.faizul.infra.seeder;

import io.github.faizul.security.role.Role;
import io.github.faizul.security.role.RoleRepository;
import io.github.faizul.security.role.Roles;
import io.github.faizul.User.core.User;
import io.github.faizul.User.core.UserRepository;
import io.github.faizul.security.userrole.UserRole;
import io.github.faizul.security.userrole.UserRoleRepository;
import io.github.faizul.setting.AppSetting;
import io.github.faizul.setting.AppSettingRepository;
import io.github.faizul.infra.config.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final AppSettingRepository appSettingRepository;

    @Value("${app.seed.admin-password}")
    private String adminSeedPassword;

    @Value("${app.seed.user-password}")
    private String userSeedPassword;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting database seeder...");
        java.io.File logFile = new java.io.File("seeder_status.log");
        try {
            java.nio.file.Files.writeString(logFile.toPath(), "=== SEEDER STARTING AT " + java.time.LocalDateTime.now() + " ===\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            log.error("Failed to write to seeder_status.log: {}", e.getMessage());
        }

        createSchema()
                .then(seedRoles())
                .then(seedUsers())
                .then(seedAppSettings())
                .subscribe(
                        success -> {
                            log.info("Successfully seeded users, roles, and settings!");
                            try {
                                java.nio.file.Files.writeString(logFile.toPath(), "SEEDER SUCCESS: Successfully seeded users, roles, and settings!\n", java.nio.file.StandardOpenOption.APPEND);
                            } catch (Exception e) {}
                        },
                        error -> {
                            log.error("Error seeding database: {}", error.getMessage());
                            try {
                                java.io.StringWriter sw = new java.io.StringWriter();
                                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                                error.printStackTrace(pw);
                                java.nio.file.Files.writeString(logFile.toPath(), "SEEDER ERROR: " + error.getMessage() + "\n" + sw.toString() + "\n", java.nio.file.StandardOpenOption.APPEND);
                            } catch (Exception e) {}
                        },
                        () -> {
                            log.info("Database seeding completed.");
                            try {
                                java.nio.file.Files.writeString(logFile.toPath(), "SEEDER COMPLETED.\n", java.nio.file.StandardOpenOption.APPEND);
                            } catch (Exception e) {}
                        }
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
        Mono<User> seedAdmin = userRepository.findByEmail("admin@mail.com")
                .flatMap(existingAdmin -> {
                    log.info("Admin user already exists. Ensuring user is active...");
                    if (!Boolean.TRUE.equals(existingAdmin.getIsActive())) {
                        existingAdmin.setIsActive(true);
                        return userRepository.save(existingAdmin);
                    }
                    return Mono.just(existingAdmin);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Admin user not found. Seeding admin user...");
                    User admin = User.builder()
                            .username("admin")
                            .email("admin@mail.com")
                            .password(passwordEncoder.encode(adminSeedPassword))
                            .isActive(true)
                            .build();

                    return userRepository.save(admin)
                            .zipWith(roleRepository.findByName(Roles.ADMIN))
                            .flatMap(tuple -> userRoleRepository.save(
                                    UserRole.builder()
                                            .userId(tuple.getT1().getId())
                                            .roleId(tuple.getT2().getId())
                                            .build()
                            ).thenReturn(tuple.getT1()));
                }));

        Mono<User> seedRegularUser = userRepository.findByEmail("user@mail.com")
                .flatMap(existingUser -> {
                    log.info("Regular user already exists. Ensuring user is active...");
                    if (!Boolean.TRUE.equals(existingUser.getIsActive())) {
                        existingUser.setIsActive(true);
                        return userRepository.save(existingUser);
                    }
                    return Mono.just(existingUser);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Regular user not found. Seeding regular user...");
                    User user = User.builder()
                            .username("user")
                            .email("user@mail.com")
                            .password(passwordEncoder.encode(userSeedPassword))
                            .isActive(true)
                            .build();

                    return userRepository.save(user)
                            .zipWith(roleRepository.findByName(Roles.USER))
                            .flatMap(tuple -> userRoleRepository.save(
                                    UserRole.builder()
                                            .userId(tuple.getT1().getId())
                                            .roleId(tuple.getT2().getId())
                                            .build()
                            ).thenReturn(tuple.getT1()));
                }));

        return seedAdmin.then(seedRegularUser).then();
    }

    private Mono<Void> createSchema() {
        String schema = """
                CREATE TABLE IF NOT EXISTS users (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    username VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    storage_quota BIGINT DEFAULT 1073741824,
                    full_name VARCHAR(255),
                    avatar_url VARCHAR(1024),
                    phone_number VARCHAR(50),
                    is_active BOOLEAN DEFAULT TRUE,
                    migration_daily_limit INTEGER DEFAULT 3,
                    migration_max_file_size BIGINT DEFAULT 268435456,
                    subscription_tier VARCHAR(255) DEFAULT 'FREEMIUM',
                    subscription_expires_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted_at TIMESTAMP
                );
                CREATE TABLE IF NOT EXISTS roles (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
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
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    token VARCHAR(255) NOT NULL,
                    revoked BOOLEAN DEFAULT FALSE,
                    expired_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
                CREATE TABLE IF NOT EXISTS external_users (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    user_id BIGINT,
                    provider VARCHAR(255) NOT NULL,
                    provider_user_id VARCHAR(255) NOT NULL,
                    email VARCHAR(255) NOT NULL,
                    access_token TEXT,
                    refresh_token TEXT,
                    expires_at BIGINT,
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
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
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
                CREATE TABLE IF NOT EXISTS otp_verifications (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
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
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    file_id UUID NOT NULL UNIQUE,
                    summary TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
                );
                 ALTER TABLE file_shared ALTER COLUMN user_id DROP NOT NULL;
                 ALTER TABLE file_shared ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
                 ALTER TABLE file_shared ADD COLUMN IF NOT EXISTS share_token VARCHAR(255) UNIQUE;
                 ALTER TABLE file_shared ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT FALSE;
                 CREATE TABLE IF NOT EXISTS app_settings (
                     id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                     setting_key VARCHAR(255) NOT NULL UNIQUE,
                     setting_value TEXT NOT NULL,
                     description VARCHAR(1024)
                 );
                 CREATE TABLE IF NOT EXISTS user_activities (
                     id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                     user_id BIGINT,
                     activity_type VARCHAR(255) NOT NULL,
                     description TEXT,
                     ip_address VARCHAR(45),
                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                 );
                 CREATE TABLE IF NOT EXISTS ai_token_logs (
                     id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                     user_id BIGINT,
                     activity_type VARCHAR(50) NOT NULL,
                     provider VARCHAR(50) NOT NULL,
                     model_name VARCHAR(255) NOT NULL,
                     input_tokens INTEGER,
                     output_tokens INTEGER,
                     total_tokens INTEGER,
                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                 );
                 ALTER TABLE users ADD COLUMN IF NOT EXISTS ai_daily_limit INTEGER DEFAULT 5;
                 ALTER TABLE users ADD COLUMN IF NOT EXISTS daily_ai_requests INTEGER DEFAULT 0;
                 ALTER TABLE users ADD COLUMN IF NOT EXISTS last_ai_request_date DATE DEFAULT CURRENT_DATE;
                 ALTER TABLE users ADD COLUMN IF NOT EXISTS migration_daily_limit INTEGER DEFAULT 3;
                 ALTER TABLE users ADD COLUMN IF NOT EXISTS migration_max_file_size BIGINT DEFAULT 268435456;
                 CREATE TABLE IF NOT EXISTS migration_tasks (
                     id UUID PRIMARY KEY,
                     batch_id UUID NOT NULL,
                     user_id BIGINT NOT NULL,
                     file_id UUID NOT NULL,
                     file_name VARCHAR(255),
                     source_provider VARCHAR(50) NOT NULL,
                     target_provider VARCHAR(50) NOT NULL,
                     target_external_account_id BIGINT,
                     delete_source BOOLEAN DEFAULT FALSE,
                     status VARCHAR(50) NOT NULL,
                     progress DOUBLE PRECISION DEFAULT 0.0,
                     error_message TEXT,
                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                     FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE,
                     FOREIGN KEY (target_external_account_id) REFERENCES external_users(id) ON DELETE SET NULL
                 );
                 ALTER TABLE migration_tasks ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);
                 CREATE TABLE IF NOT EXISTS subscription_requests (
                     id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                     user_id BIGINT NOT NULL,
                     requested_tier VARCHAR(255) NOT NULL,
                     status VARCHAR(50) DEFAULT 'PENDING',
                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                 );
                 ALTER TABLE users ADD COLUMN IF NOT EXISTS subscription_tier VARCHAR(255) DEFAULT 'FREEMIUM';
                 ALTER TABLE users ADD COLUMN IF NOT EXISTS subscription_expires_at TIMESTAMP;
                 """;
        log.info("Initializing database schema...");
        return Flux.fromArray(schema.split(";"))
                .map(String::trim)
                .filter(sql -> !sql.isEmpty())
                .concatMap(sql -> databaseClient.sql(sql).then())
                .then();
    }

    private Mono<Void> seedAppSettings() {
        return Flux.just(
                AppSetting.builder()
                        .key("ai.summary.primary.provider")
                        .value(AiConfig.SUMMARY_PRIMARY_PROVIDER)
                        .description("Penyedia model utama untuk rangkuman")
                        .build(),
                AppSetting.builder()
                        .key("ai.summary.primary.model")
                        .value(AiConfig.SUMMARY_PRIMARY_MODEL)
                        .description("Model utama untuk rangkuman")
                        .build(),
                AppSetting.builder()
                        .key("ai.summary.fallback.provider")
                        .value(AiConfig.SUMMARY_FALLBACK_PROVIDER)
                        .description("Penyedia model fallback untuk rangkuman")
                        .build(),
                AppSetting.builder()
                        .key("ai.summary.fallback.model")
                        .value(AiConfig.SUMMARY_FALLBACK_MODEL)
                        .description("Model fallback untuk rangkuman")
                        .build(),
                AppSetting.builder()
                        .key("ai.summary.fallback.provider.two")
                        .value(AiConfig.SUMMARY_FALLBACK_PROVIDER_TWO)
                        .description("Penyedia model fallback 2 untuk rangkuman")
                        .build(),
                AppSetting.builder()
                        .key("ai.summary.fallback.model.two")
                        .value(AiConfig.SUMMARY_FALLBACK_MODEL_TWO)
                        .description("Model fallback 2 untuk rangkuman")
                        .build(),
                AppSetting.builder()
                        .key("ai.chat.primary.provider")
                        .value(AiConfig.CHAT_PRIMARY_PROVIDER)
                        .description("Penyedia model utama untuk chat PDF")
                        .build(),
                AppSetting.builder()
                        .key("ai.chat.primary.model")
                        .value(AiConfig.CHAT_PRIMARY_MODEL)
                        .description("Model utama untuk chat PDF")
                        .build(),
                AppSetting.builder()
                        .key("ai.chat.fallback.provider")
                        .value(AiConfig.CHAT_FALLBACK_PROVIDER)
                        .description("Penyedia model fallback untuk chat PDF")
                        .build(),
                AppSetting.builder()
                        .key("ai.chat.fallback.model")
                        .value(AiConfig.CHAT_FALLBACK_MODEL)
                        .description("Model fallback untuk chat PDF")
                        .build(),
                AppSetting.builder()
                        .key("ai.chat.fallback.provider.two")
                        .value(AiConfig.CHAT_FALLBACK_PROVIDER_TWO)
                        .description("Penyedia model fallback 2 untuk chat PDF")
                        .build(),
                AppSetting.builder()
                        .key("ai.chat.fallback.model.two")
                        .value(AiConfig.CHAT_FALLBACK_MODEL_TWO)
                        .description("Model fallback 2 untuk chat PDF")
                        .build(),
                AppSetting.builder()
                        .key("ai.guardrail.user_daily_request_limit")
                        .value("5")
                        .description("Batas default request harian AI per user jika tidak diatur khusus")
                        .build(),
                AppSetting.builder()
                        .key("ai.system_prompt")
                        .value("Anda adalah asisten AI yang bertugas merangkum teks atau dokumen dalam Bahasa Indonesia. Rangkum isi teks/dokumen secara singkat, padat, jelas, dan terstruktur. Jika dokumen sangat pendek (seperti kartu identitas, sertifikat, atau kuitansi), berikan ringkasan informasi penting secara langsung tanpa menolaknya. Jika input tidak berisi informasi yang dapat dirangkum (misalnya hanya sapaan kosong atau teks acak tanpa makna), Anda WAJIB menjawab: \"Maaf, input tidak dapat diproses.\"")
                        .description("System prompt utama untuk AI")
                        .build(),
                AppSetting.builder()
                        .key("ai.summary.system_prompt")
                        .value(AiConfig.SUMMARY_SYSTEM_PROMPT)
                        .description("System prompt khusus untuk rangkuman")
                        .build(),
                AppSetting.builder()
                        .key("ai.chat.system_prompt")
                        .value(AiConfig.CHAT_SYSTEM_PROMPT)
                        .description("System prompt khusus untuk chat PDF")
                        .build(),
                AppSetting.builder()
                        .key("migration.max_file_size_bytes")
                        .value("268435456")
                        .description("Batas maksimum ukuran satu berkas yang diizinkan untuk migrasi (dalam bytes)")
                        .build(),
                AppSetting.builder()
                        .key("migration.max_daily_limit")
                        .value("3")
                        .description("Batas harian maksimum migrasi per user")
                        .build()
        )
        .flatMap(setting -> appSettingRepository.findByKey(setting.getKey())
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Seeding setting: {}...", setting.getKey());
                    return appSettingRepository.save(setting);
                })))
        .then();
    }
}
