package com.smartgrievance.common.web;

import com.smartgrievance.common.exception.FormRejectedException;
import org.springframework.validation.BindingResult;

public final class Forms {
    private Forms() {}

    public static void apply(FormRejectedException rejection, BindingResult binding) {
        for (var error : rejection.errors()) {
            if (error.field() == null) binding.reject(error.key(), error.args(), null);
            else binding.rejectValue(error.field(), error.key(), error.args(), null);
        }
    }
}
