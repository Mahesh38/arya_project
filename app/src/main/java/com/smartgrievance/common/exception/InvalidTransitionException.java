package com.smartgrievance.common.exception;

public class InvalidTransitionException extends ActionRefusedException {
    public InvalidTransitionException(String key, Subject subject, Object... args) {
        super("INVALID_TRANSITION", key, subject, args);
    }
}
