package com.smartgrievance.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

abstract class NameForm {
    @NotBlank(message = "{validation.name.required}")
    @Size(min = 2, max = 100, message = "{validation.name.size}")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
