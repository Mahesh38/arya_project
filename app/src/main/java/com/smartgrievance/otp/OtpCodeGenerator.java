package com.smartgrievance.otp;

import java.security.SecureRandom;
import java.util.Locale;

import com.smartgrievance.config.AppProperties;
import org.springframework.stereotype.Component;

@Component
class OtpCodeGenerator {
    private final SecureRandom random = new SecureRandom();
    private final String format;
    private final int range;

    OtpCodeGenerator(AppProperties properties) {
        int length = properties.otp().length();
        format = "%0" + length + "d";
        range = (int) Math.pow(10, length);
    }

    String next() {
        return String.format(Locale.ROOT, format, random.nextInt(range));
    }
}
