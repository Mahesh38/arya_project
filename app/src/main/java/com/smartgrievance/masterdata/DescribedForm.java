package com.smartgrievance.masterdata;

import jakarta.validation.constraints.Size;

public class DescribedForm extends NameForm {
    @Size(max = 255, message = "{validation.optionalText.size}")
    private String description;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
