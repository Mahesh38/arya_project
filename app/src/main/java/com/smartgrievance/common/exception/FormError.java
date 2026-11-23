package com.smartgrievance.common.exception;

public record FormError(String field, String key, Object... args) {
    public FormError {
        args = args.clone();
    }

    @Override
    public Object[] args() { return args.clone(); }
}
