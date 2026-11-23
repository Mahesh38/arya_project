package com.smartgrievance.common.validation;

import java.util.Objects;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class FieldsDifferValidator implements ConstraintValidator<FieldsDiffer, Object> {
    private FieldsDiffer constraint;

    @Override
    public void initialize(FieldsDiffer constraint) {
        this.constraint = constraint;
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) return true;
        var form = new BeanWrapperImpl(value);
        Object right = form.getPropertyValue(constraint.second());
        if (right == null || "".equals(right)) return true;
        Object left = form.getPropertyValue(constraint.first());
        if (Objects.equals(left, right)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(constraint.message())
                    .addPropertyNode(constraint.second()).addConstraintViolation();
            return false;
        }
        return true;
    }
}
