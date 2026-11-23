package com.smartgrievance.common.validation;

import java.util.Locale;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static boolean containsPersonalData(String password, String email, String mobile) {
        String normalized = password.toLowerCase(Locale.ROOT);
        int at = email.indexOf('@');
        return (at >= 4 && normalized.contains(email.substring(0, at).toLowerCase(Locale.ROOT)))
                || (mobile != null && mobile.length() == 10 && normalized.contains(mobile));
    }
}
