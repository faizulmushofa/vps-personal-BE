package io.github.faizul.report.controller;

import io.github.faizul.activity.service.UserActivityService;
import io.github.faizul.notification.service.EmailTemplateFactory;
import io.github.faizul.notification.service.NotificationService;
import io.github.faizul.report.dtos.ReportRequest;
import io.github.faizul.security.auth.service.IpRateLimiter;
import io.github.faizul.security.filter.CurrentUserContext;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;



@RestController
@RequestMapping("api/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final NotificationService notificationService;
    private final CurrentUserContext currentUserContext;
    private final IpRateLimiter rateLimiter;
    private final UserActivityService userActivityService;

    /**
     * OWASP A08 FIX: Sanitize HTML in description to prevent email-based XSS/phishing
     */
    private String sanitizeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    @PostMapping
    public Mono<ResponseEntity<Void>> submitReport(@RequestBody ReportRequest request, ServerWebExchange exchange) {
        if (request.description() == null || request.description().trim().length() < 5) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        // OWASP A08 FIX: Rate limit (3 per hour)
        if (rateLimiter.isRateLimited(exchange, "report", 3, 60 * 60 * 1000L)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build());
        }

        // OWASP A08 FIX: Limit description length
        String description = request.description().trim();
        if (description.length() > 2000) {
            description = description.substring(0, 2000);
        }

        // OWASP A08 FIX: Sanitize HTML
        String sanitizedDescription = sanitizeHtml(description);

        final String finalDescription = sanitizedDescription;

        return currentUserContext.getUserId()
                .flatMap(userId -> currentUserContext.getUser()
                        .flatMap(userDetails -> {
                            String userEmail = sanitizeHtml(userDetails.getUsername());
                            String subject = "Laporan Kendala Baru dari Pengguna - Horizon Drive";
                            String htmlBody = EmailTemplateFactory.getBugReportTemplate(userEmail, finalDescription);
                            
                            log.info("Mengirimkan laporan bug ke developer dari user: {}", userEmail);
                            return notificationService.sendNotification("emuyforge@gmail.com", subject, htmlBody)
                                    .then(userActivityService.log(userId, "SUBMIT_BUG_REPORT", "Mengirimkan laporan kendala/bug ke pengembang", exchange));
                        })
                )
                // Fallback jika dikirim tanpa login (anonim)
                .onErrorResume(e -> {
                    String subject = "Laporan Kendala Baru (Anonim) - Horizon Drive";
                    String htmlBody = EmailTemplateFactory.getBugReportTemplate("Anonymous (Not Logged In)", finalDescription);
                    
                    log.info("Mengirimkan laporan bug anonim ke developer");
                    return notificationService.sendNotification("emuyforge@gmail.com", subject, htmlBody)
                            .then(userActivityService.log(null, "SUBMIT_BUG_REPORT", "Mengirimkan laporan kendala/bug secara anonim", exchange));
                })
                .then(Mono.just(ResponseEntity.ok().build()));
    }
}
