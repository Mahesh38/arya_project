package com.smartgrievance.masterdata;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CategoryForm extends NameForm {
    private Long defaultDepartmentId;

    @NotNull(message = "{validation.slaDays.required}")
    @Min(value = 1, message = "{validation.slaDays.range}")
    @Max(value = 90, message = "{validation.slaDays.range}")
    private Integer slaDays;

    public Long getDefaultDepartmentId() { return defaultDepartmentId; }
    public void setDefaultDepartmentId(Long id) { defaultDepartmentId = id; }
    public Integer getSlaDays() { return slaDays; }
    public void setSlaDays(Integer slaDays) { this.slaDays = slaDays; }
}
