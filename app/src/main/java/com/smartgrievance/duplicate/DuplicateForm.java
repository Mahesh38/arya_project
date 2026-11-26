package com.smartgrievance.duplicate;

import com.smartgrievance.complaint.RemarkForm;
import jakarta.validation.constraints.Pattern;

public class DuplicateForm extends RemarkForm {
    static final String NUMBER_PATTERN = "^GRV-[0-9]{4}-[0-9]{6}$";

    @jakarta.validation.constraints.NotBlank(message = "{validation.originalNo.required}")
    @Pattern(regexp = NUMBER_PATTERN, message = "{validation.originalNo.format}")
    private String originalNo;

    public String getOriginalNo() { return originalNo; }
    public void setOriginalNo(String originalNo) { this.originalNo = originalNo; }
}
