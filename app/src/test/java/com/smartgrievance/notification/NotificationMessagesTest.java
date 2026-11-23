package com.smartgrievance.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.ClassPathResource;

class NotificationMessagesTest {
    @Test
    @DisplayName("TC-UT-10, TC-BR-23 every fixed notification template fits the database")
    void rendersEveryTemplateAtMaximumArgumentLengths() throws Exception {
        var bundle = new ResourceBundleMessageSource();
        bundle.setBasename("messages");
        bundle.setDefaultEncoding("UTF-8");
        var messages = new NotificationMessages(bundle);
        var keys = new Properties();
        try (var input = new ClassPathResource("messages.properties").getInputStream()) {
            keys.load(input);
        }
        String number = "GRV-2026-999999";
        String note = NotificationMessages.excerpt("message ".repeat(70), 120);
        assertThat(note).hasSize(120).endsWith("message…");
        assertThat(NotificationMessages.excerpt("x".repeat(500), 120)).hasSize(121);
        assertThat(NotificationMessages.excerpt(null, 120)).isNull();
        var notificationKeys = keys.stringPropertyNames().stream().filter(key -> key.startsWith("notify.")).toList();
        assertThat(notificationKeys).hasSize(20);
        for (String key : notificationKeys) {
            List<String> arguments = switch (key) {
                case "notify.submitted.admin" -> List.of("t".repeat(100) + "…");
                case "notify.assigned.citizen" -> List.of("d".repeat(100));
                case "notify.reassigned.citizen", "notify.sentBack" -> List.of("d".repeat(100), note);
                case "notify.resolved.owner" -> List.of(note, "23-Nov-2026");
                case "notify.duplicate.owner", "notify.duplicate.watcher" -> List.of(number);
                case "notify.autoClosed" -> List.of("7");
                default -> List.of(note);
            };
            var all = new java.util.ArrayList<String>();
            all.add(number);
            all.addAll(arguments);
            assertThat(messages.render(key, all.toArray())).as(key).contains(number).doesNotContain("{1}", "{2}")
                    .hasSizeLessThanOrEqualTo(300);
        }
    }
}
