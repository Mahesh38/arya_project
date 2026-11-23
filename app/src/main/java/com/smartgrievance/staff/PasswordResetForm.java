package com.smartgrievance.staff;

import com.smartgrievance.common.validation.FieldsMatch;
import com.smartgrievance.common.validation.StrongPassword;
import jakarta.validation.constraints.NotEmpty;

@FieldsMatch(first = "newPassword", second = "confirmPassword", message = "{validation.confirmPassword.mismatch}")
public record PasswordResetForm(
        @NotEmpty(message = "{validation.password.required}")
        @StrongPassword(message = "{validation.password.tooShort}") String newPassword,
        @NotEmpty(message = "{validation.confirmPassword.required}") String confirmPassword) {
    @Override
    public String toString() { return "PasswordResetForm"; }
}
