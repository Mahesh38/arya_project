package com.smartgrievance.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SecurityEvents {
    public static final Logger LOG = LoggerFactory.getLogger("SECURITY");

    private SecurityEvents() {}
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0 || email.length() > 254) return "***";
        String domain = email.substring(at + 1);
        if (!domain.matches("[a-z0-9.-]+")) return "***";
        char first = email.charAt(0);
        return (Character.isLetterOrDigit(first) ? first : '*') + "***@" + domain;
    }

    static String maskIp(String ip) {
        int separator = Math.max(ip.lastIndexOf('.'), ip.lastIndexOf(':'));
        return separator < 0 ? "***" : ip.substring(0, separator + 1) + "***";
    }

    static void sessionEnded(long id, String reason) {
        LOG.info("SESSION_ENDED userId={} reason={}", id, reason);
    }
}
