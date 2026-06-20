package io.github.faizul.notification.service.impl;

import io.github.faizul.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;



@ExtendWith(MockitoExtension.class)
class EmailNotificationTest {

    private EmailNotification emailNotification;

    @BeforeEach
    void setUp() {
        emailNotification = new EmailNotification();
        ReflectionTestUtils.setField(emailNotification, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(emailNotification, "senderEmail", "noreply@test.com");
        ReflectionTestUtils.setField(emailNotification, "senderName", "Test Cloud");
    }

    @Test
    @DisplayName("should have NotificationService implementation")
    void isNotificationService() {
        assertThat(emailNotification).isInstanceOf(NotificationService.class);
    }

    @Test
    @DisplayName("should have properly configured sender fields")
    void hasConfiguredFields() {
        String apiKey = (String) ReflectionTestUtils.getField(emailNotification, "apiKey");
        String senderEmail = (String) ReflectionTestUtils.getField(emailNotification, "senderEmail");
        String senderName = (String) ReflectionTestUtils.getField(emailNotification, "senderName");

        assertThat(apiKey).isEqualTo("test-api-key");
        assertThat(senderEmail).isEqualTo("noreply@test.com");
        assertThat(senderName).isEqualTo("Test Cloud");
    }
}
