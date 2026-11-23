package com.smartgrievance.otp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class OtpForm {
    @NotBlank(message = "{validation.code.required}")
    @Pattern(regexp = "^\\d{6}$", message = "{validation.code.format}")
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
