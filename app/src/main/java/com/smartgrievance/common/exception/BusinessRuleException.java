package com.smartgrievance.common.exception;

public class BusinessRuleException extends ActionRefusedException {
    private final String rule;

    public BusinessRuleException(String rule, String key, Subject subject, Object... args) {
        super("BUSINESS_RULE", key, subject, args);
        this.rule = rule;
    }

    public String rule() { return rule; }
}
