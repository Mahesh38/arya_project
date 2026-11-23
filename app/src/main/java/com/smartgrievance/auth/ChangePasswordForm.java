package com.smartgrievance.auth;

import com.smartgrievance.common.validation.FieldsDiffer;
import com.smartgrievance.common.validation.FieldsMatch;
import com.smartgrievance.common.validation.StrongPassword;
import jakarta.validation.constraints.NotEmpty;

@FieldsMatch(first = "newPassword", second = "confirmPassword", message = "{validation.confirmPassword.mismatch}")
@FieldsDiffer(first = "currentPassword", second = "newPassword", message = "{validation.newPassword.sameAsCurrent}")
public record ChangePasswordForm(
        @NotEmpty(message = "{validation.currentPassword.required}") String currentPassword,
        @NotEmpty(message = "{validation.password.required}")
        @StrongPassword(message = "{validation.password.tooShort}") String newPassword,
        @NotEmpty(message = "{validation.confirmPassword.required}") String confirmPassword) {

    @Override
    public String toString() { return "ChangePasswordForm"; }
}
