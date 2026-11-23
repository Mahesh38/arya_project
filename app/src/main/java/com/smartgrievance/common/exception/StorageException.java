package com.smartgrievance.common.exception;

public class StorageException extends AppException {
    public StorageException(Throwable cause) {
        super("STORAGE_FAILED", "validation.complaint.saveFailed", cause, new Object[0]);
    }
}
