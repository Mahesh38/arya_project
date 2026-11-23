package com.smartgrievance.common.exception;

import java.util.List;

public class FormRejectedException extends AppException {
    private final List<FormError> errors;

    public FormRejectedException(List<FormError> errors) {
        super("FORM_REJECTED", errors.getFirst().key(), errors.getFirst().args());
        this.errors = List.copyOf(errors);
    }

    public List<FormError> errors() { return errors; }
}
