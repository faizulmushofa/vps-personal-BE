package io.github.faizul.admin;

import io.github.faizul.activity.UserActivity;
import io.github.faizul.activity.UserActivityService;
import io.github.faizul.setting.AppSetting;
import io.github.faizul.setting.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AppSettingService appSettingService;
    private final UserActivityService userActivityService;
    private final io.github.faizul.security.filter.CurrentUserContext currentUserContext;

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
    public Mono<Void> toggleUserStatus(@PathVariable Long id, @RequestBody ToggleStatusRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(adminId -> adminService.toggleUserStatus(id, request.isActive())
                        .then(userActivityService.log(adminId, "TOGGLE_USER_STATUS", 
                                "Mengubah status aktif user (ID: " + id + ") menjadi " + request.isActive(), exchange))
                        .then()
                );
    }

    @PutMapping("/users/{id}/ai-limit")
    public Mono<Void> updateUserAiLimit(@PathVariable Long id, @RequestBody UpdateAiLimitRequest request, org.springframework.web.server.ServerWebExchange exchange) {
        return currentUserContext.getUserId()
                .flatMap(adminId -> adminService.updateUserAiLimit(id, request.aiLimit())
                        .then(userActivityService.log(adminId, "UPDATE_USER_AI_LIMIT", 
                                "Mengubah batas request AI harian user (ID: " + id + ") menjadi " + request.aiLimit() + " request", exchange))
                        .then()
                );
    }

    @GetMapping("/activities")
    public Flux<UserActivity> getUserActivities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return userActivityService.getAllActivities(PageRequest.of(page, size));
    }

    @GetMapping("/ai/token-stats")
    public Mono<AiTokenStats> getAiTokenStats() {
        return adminService.getAiTokenStats();
    }

    // Inner request records
    public record ToggleStatusRequest(Boolean isActive) {}
    public record UpdateAiLimitRequest(Integer aiLimit) {}
}
