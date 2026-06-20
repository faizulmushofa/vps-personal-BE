package io.github.faizul.notification.service;

import reactor.core.publisher.Mono;

public interface NotificationService {
    Mono<Void> sendNotification(String to, String subject, String body);
}
