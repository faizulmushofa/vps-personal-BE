package io.github.faizul.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import io.github.faizul.notification.service.NotificationService;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class EmailController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    public Mono<String> sendEmail(
            @RequestParam String to,
            @RequestParam String subject,
            @RequestParam String body) {
        return notificationService.sendNotification(to, subject, body)
                .thenReturn("Email sent successfully to " + to);
    }
}
