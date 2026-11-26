package com.smartgrievance.common.web;

import java.security.SecureRandom;

public final class ErrorReference {
    static final String ATTRIBUTE = "app.errorReference";
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ErrorReference() {}

    public static String next() {
        StringBuilder reference = new StringBuilder("ERR-");
        for (int i = 0; i < 8; i++) {
            if (i == 4) reference.append('-');
            reference.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return reference.toString();
    }
}
