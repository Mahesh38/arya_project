package com.smartgrievance.common.exception;

public record Subject(Type type, String ref) {
    public enum Type { COMPLAINT, OTP, EXPORT, DEPARTMENTS, CATEGORIES, AREAS, OFFICERS, CITIZENS }

    public Subject(Type type) {
        this(type, null);
    }
}
