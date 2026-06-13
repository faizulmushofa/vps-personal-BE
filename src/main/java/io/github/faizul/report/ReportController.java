package io.github.faizul.report;

import io.github.faizul.notification.NotificationService;
import io.github.faizul.security.filter.CurrentUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final NotificationService notificationService;
    private final CurrentUserContext currentUserContext;

    @PostMapping
    public Mono<ResponseEntity<Void>> submitReport(@RequestBody ReportRequest request) {
        if (request.description() == null || request.description().trim().length() < 5) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return currentUserContext.getUser()
                .flatMap(userDetails -> {
                    String userEmail = userDetails.getUsername(); // email user
                    String subject = "Laporan Kendala Baru dari Pengguna - Horizon Drive";
                    String body = String.format(
                            "Halo Developer,\n\n" +
                            "Telah diterima laporan kendala baru di aplikasi Horizon Drive:\n\n" +
                            "Pengirim: %s\n" +
                            "Deskripsi Kendala:\n" +
                            "%s\n\n" +
                            "Salam,\n" +
                            "Sistem Horizon Cloud",
                            userEmail, request.description()
                    );
                    
                    log.info("Mengirimkan laporan bug ke developer dari user: {}", userEmail);
                    return notificationService.sendNotification("emuyforge@gmail.com", subject, body);
                })
                // Fallback jika dikirim tanpa login (anonim)
                .onErrorResume(e -> {
                    String subject = "Laporan Kendala Baru (Anonim) - Horizon Drive";
                    String body = String.format(
                            "Halo Developer,\n\n" +
                            "Telah diterima laporan kendala baru di aplikasi Horizon Drive (dikirim secara anonim/belum login):\n\n" +
                            "Deskripsi Kendala:\n" +
                            "%s\n\n" +
                            "Salam,\n" +
                            "Sistem Horizon Cloud",
                            request.description()
                    );
                    log.info("Mengirimkan laporan bug anonim ke developer");
                    return notificationService.sendNotification("emuyforge@gmail.com", subject, body);
                })
                .then(Mono.just(ResponseEntity.ok().build()));
    }
}
