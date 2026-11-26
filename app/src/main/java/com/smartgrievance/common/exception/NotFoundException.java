package com.smartgrievance.common.exception;

public class NotFoundException extends AppException {
    private final String object;
    private final String ref;
    private final boolean policy;

    public NotFoundException(String object, String ref, boolean policy) {
        super("NOT_FOUND", "page.error.404.text");
        this.object = object;
        this.ref = ref;
        this.policy = policy;
    }

    public String object() { return object; }
    public String ref() { return ref; }
    public boolean policy() { return policy; }
}
