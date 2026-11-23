package com.smartgrievance.common.exception;

public abstract class AppException extends RuntimeException {
    private final String code;
    private final String messageKey;
    private final Object[] args;

    protected AppException(String code, String messageKey, Object... args) {
        this(code, messageKey, null, args);
    }

    protected AppException(String code, String messageKey, Throwable cause, Object... args) {
        super(code + " " + messageKey, cause);
        this.code = code;
        this.messageKey = messageKey;
        this.args = args.clone();
    }

    public String code() { return code; }
    public String messageKey() { return messageKey; }
    public Object[] args() { return args.clone(); }
}
