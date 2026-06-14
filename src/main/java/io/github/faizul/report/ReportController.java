package io.github.faizul.report;

import io.github.faizul.notification.EmailTemplateFactory;
import io.github.faizul.notification.NotificationService;
import io.github.faizul.security.filter.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("api/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final NotificationService notificationService;
    private final CurrentUserContext currentUserContext;

    // OWASP A08 FIX: Rate limit reports per IP (3 per hour)
    private static final int MAX_REPORTS_PER_WINDOW = 3;
    private static final long REPORT_WINDOW_MS = 60 * 60 * 1000L; // 1 hour

    private record ReportRateEntry(AtomicInteger count, long windowStart) {}
    private final Map<String, ReportRateEntry> reportRateMap = new ConcurrentHashMap<>();

    private boolean isReportRateLimited(ServerWebExchange exchange) {
        String ip = extractIp(exchange);
        long now = Instant.now().toEpochMilli();

        ReportRateEntry entry = reportRateMap.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.windowStart() > REPORT_WINDOW_MS) {
                return new ReportRateEntry(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });

        return entry.count().get() > MAX_REPORTS_PER_WINDOW;
    }

    private String extractIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var remoteAddr = exchange.getRequest().getRemoteAddress();
        return remoteAddr != null ? remoteAddr.getAddress().getHostAddress() : "unknown";
    }

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

        // OWASP A08 FIX: Rate limit
        if (isReportRateLimited(exchange)) {
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

        return currentUserContext.getUser()
                .flatMap(userDetails -> {
                    String userEmail = sanitizeHtml(userDetails.getUsername());
                    String subject = "Laporan Kendala Baru dari Pengguna - Horizon Drive";
                    String htmlBody = EmailTemplateFactory.getBugReportTemplate(userEmail, finalDescription);
                    
                    log.info("Mengirimkan laporan bug ke developer dari user: {}", userEmail);
                    return notificationService.sendNotification("emuyforge@gmail.com", subject, htmlBody);
                })
                // Fallback jika dikirim tanpa login (anonim)
                .onErrorResume(e -> {
                    String subject = "Laporan Kendala Baru (Anonim) - Horizon Drive";
                    String htmlBody = EmailTemplateFactory.getBugReportTemplate("Anonymous (Not Logged In)", finalDescription);
                    
                    log.info("Mengirimkan laporan bug anonim ke developer");
                    return notificationService.sendNotification("emuyforge@gmail.com", subject, htmlBody);
                })
                .then(Mono.just(ResponseEntity.ok().build()));
    }
}
