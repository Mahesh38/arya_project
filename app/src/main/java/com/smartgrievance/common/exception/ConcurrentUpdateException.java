package com.smartgrievance.common.exception;

public class ConcurrentUpdateException extends ActionRefusedException {
    public ConcurrentUpdateException(String key, Subject subject, Object... args) {
        super("CONCURRENT_UPDATE", key, subject, args);
    }
}
