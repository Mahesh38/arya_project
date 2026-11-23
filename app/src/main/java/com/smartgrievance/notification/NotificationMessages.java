package com.smartgrievance.notification;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class NotificationMessages {
    private final MessageSource messages;

    public NotificationMessages(MessageSource messages) { this.messages = messages; }

    public String render(String key, Object... args) { return messages.getMessage(key, args, Locale.ENGLISH); }

    public static String excerpt(String text, int limit) {
        if (text == null || text.length() <= limit) return text;
        int end = text.lastIndexOf(' ', limit);
        if (end <= 0) end = Character.isHighSurrogate(text.charAt(limit - 1)) ? limit - 1 : limit;
        return text.substring(0, end) + "…";
    }
}
