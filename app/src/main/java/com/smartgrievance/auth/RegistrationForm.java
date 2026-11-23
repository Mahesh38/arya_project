package com.smartgrievance.auth;

import com.smartgrievance.common.validation.FieldsMatch;
import com.smartgrievance.common.validation.StrongPassword;
import com.smartgrievance.common.validation.ValidMobile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@FieldsMatch(first = "password", second = "confirmPassword", message = "{validation.confirmPassword.mismatch}")
public class RegistrationForm {
    @NotBlank(message = "{validation.fullName.required}")
    @Size(min = 2, max = 100, message = "{validation.fullName.size}")
    @Pattern(regexp = "^[\\p{L}\\p{M} .'’-]*$", message = "{validation.fullName.chars}")
    private String fullName;

    @NotBlank(message = "{validation.email.required}")
    @Size(max = 254, message = "{validation.email.size}")
    @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "{validation.email.format}")
    private String email;

    @NotBlank(message = "{validation.mobile.required}")
    @ValidMobile(message = "{validation.mobile.format}")
    private String mobile;

    @NotEmpty(message = "{validation.password.required}")
    @StrongPassword(message = "{validation.password.tooShort}")
    private String password;

    @NotEmpty(message = "{validation.confirmPassword.required}")
    private String confirmPassword;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
