package io.github.faizul.admin;

import io.github.faizul.activity.dtos.UserActivityResponse;
import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.admin.dtos.AdminUserResponse;
import io.github.faizul.admin.dtos.AiTokenStats;
import io.github.faizul.security.filter.CurrentUserContext;
import io.github.faizul.setting.model.AppSetting;
import io.github.faizul.setting.service.AppSettingService;
import io.github.faizul.user.dtos.UserDto;
import io.github.faizul.user.model.SubscriptionRequest;
import io.github.faizul.user.service.SubscriptionRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;



@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AppSettingService appSettingService;
    private final UserActivityService userActivityService;
    private final CurrentUserContext currentUserContext;
    private final SubscriptionRequestService subscriptionRequestService;

    @GetMapping("/settings")
    public Flux<AppSetting> getSettings() {
        return appSettingService.getAllSettings();
    }

    @PutMapping("/settings")
    public Mono<Void> updateSettings(@RequestBody Map<String, String> settings, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(adminId -> appSettingService.updateSettings(settings)
                        .then(userActivityService.log(adminId, "UPDATE_SETTINGS", "Mengubah pengaturan konfigurasi AI aplikasi", exchange))
                        .then()
                );
    }

    @GetMapping("/users")
    public Flux<AdminUserResponse> getAllUsers() {
        return adminService.getAllUsers();
    }

    @PutMapping("/users/{id}/status")
    public Mono<Void> toggleUserStatus(@PathVariable Long id, @Valid @RequestBody ToggleStatusRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(adminId -> adminService.toggleUserStatus(id, request.isActive())
                        .then(userActivityService.log(adminId, "TOGGLE_USER_STATUS", 
                                "Mengubah status aktif user (ID: " + id + ") menjadi " + request.isActive(), exchange))
                        .then()
                );
    }

    @PutMapping("/users/{id}/ai-limit")
    public Mono<Void> updateUserAiLimit(@PathVariable Long id, @Valid @RequestBody UpdateAiLimitRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(adminId -> adminService.updateUserAiLimit(id, request.aiLimit())
                        .then(userActivityService.log(adminId, "UPDATE_USER_AI_LIMIT", 
                                "Mengubah batas request AI harian user (ID: " + id + ") menjadi " + request.aiLimit() + " request", exchange))
                        .then()
                );
    }

    @GetMapping("/activities")
    public Flux<UserActivityResponse> getUserActivities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return userActivityService.getAllActivitiesWithUserDetails(PageRequest.of(page, size));
    }

    @GetMapping("/ai/token-stats")
    public Mono<AiTokenStats> getAiTokenStats() {
        return adminService.getAiTokenStats();
    }

    @PutMapping("/users/{id}/migration-limit")
    public Mono<Void> updateUserMigrationLimit(@PathVariable Long id, @Valid @RequestBody UpdateMigrationLimitRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(adminId -> adminService.updateUserMigrationLimit(id, request.migrationLimit())
                        .then(userActivityService.log(adminId, "UPDATE_USER_MIGRATION_LIMIT", 
                                "Mengubah batas migrasi harian user (ID: " + id + ") menjadi " + request.migrationLimit() + " request", exchange))
                        .then()
                );
    }

    @PutMapping("/users/{id}/migration-max-size")
    public Mono<Void> updateUserMigrationMaxSize(@PathVariable Long id, @Valid @RequestBody UpdateMigrationMaxSizeRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(adminId -> adminService.updateUserMigrationMaxSize(id, request.maxFileSize())
                        .then(userActivityService.log(adminId, "UPDATE_USER_MIGRATION_MAX_SIZE", 
                                "Mengubah batas ukuran migrasi maks user (ID: " + id + ") menjadi " + request.maxFileSize() + " bytes", exchange))
                        .then()
                );
    }

    @GetMapping("/subscription-requests")
    public Flux<SubscriptionRequest> getSubscriptionRequests() {
        return subscriptionRequestService.getPendingRequests();
    }

    @PostMapping("/subscription-requests/{id}/approve")
    public Mono<UserDto> approveSubscriptionRequest(@PathVariable Long id, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(adminId -> subscriptionRequestService.approveRequest(id, exchange)
                        .flatMap(userDto -> userActivityService.log(adminId, "APPROVE_SUBSCRIPTION", 
                                "Menyetujui permintaan upgrade paket pengguna (ID: " + userDto.id() + ", Tier: " + userDto.subscriptionTier() + ")", exchange)
                                .thenReturn(userDto))
                );
    }

    @PostMapping("/subscription-requests/{id}/reject")
    public Mono<SubscriptionRequest> rejectSubscriptionRequest(@PathVariable Long id, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(adminId -> subscriptionRequestService.rejectRequest(id, exchange)
                        .flatMap(req -> userActivityService.log(adminId, "REJECT_SUBSCRIPTION", 
                                "Menolak permintaan upgrade paket pengguna (ID: " + req.getUserId() + ", Tier: " + req.getRequestedTier() + ")", exchange)
                                .thenReturn(req))
                );
    }

    @PutMapping("/users/{id}/subscription")
    public Mono<UserDto> directUpdateSubscription(@PathVariable Long id, @RequestParam String tier, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(adminId -> subscriptionRequestService.directUpdateSubscription(id, tier, exchange)
                        .flatMap(userDto -> userActivityService.log(adminId, "DIRECT_UPDATE_SUBSCRIPTION", 
                                "Mengubah paket langganan pengguna secara langsung (ID: " + id + ", Tier: " + tier + ")", exchange)
                                .thenReturn(userDto))
                );
    }

    @GetMapping(value = "/users/export", produces = "text/csv")
    public org.springframework.http.ResponseEntity<Flux<String>> exportUsers() {
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users_export_" + java.time.Instant.now().getEpochSecond() + ".csv")
                .body(adminService.exportUsersCsv());
    }

    @GetMapping(value = "/activities/export", produces = "text/csv")
    public org.springframework.http.ResponseEntity<Flux<String>> exportActivities() {
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=activities_export_" + java.time.Instant.now().getEpochSecond() + ".csv")
                .body(userActivityService.exportActivitiesCsv());
    }

    @GetMapping(value = "/ai/token-logs/export", produces = "text/csv")
    public org.springframework.http.ResponseEntity<Flux<String>> exportAiTokenLogs() {
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ai_token_logs_export_" + java.time.Instant.now().getEpochSecond() + ".csv")
                .body(adminService.exportAiTokenLogsCsv());
    }

    // Inner request records
    public record ToggleStatusRequest(@NotNull(message = "Status aktif wajib diisi") Boolean isActive) {}
    public record UpdateAiLimitRequest(@NotNull(message = "AI limit wajib diisi") Integer aiLimit) {}
    public record UpdateMigrationLimitRequest(@NotNull(message = "Migration limit wajib diisi") Integer migrationLimit) {}
    public record UpdateMigrationMaxSizeRequest(@NotNull(message = "Max file size wajib diisi") Long maxFileSize) {}
}
