package com.smartgrievance.common.exception;

public abstract class ActionRefusedException extends AppException {
    private final Subject subject;

    protected ActionRefusedException(String code, String key, Subject subject, Object... args) {
        super(code, key, args);
        this.subject = subject;
    }

    public Subject subject() { return subject; }
}
