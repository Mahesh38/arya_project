package com.smartgrievance.staff;

import com.smartgrievance.auth.RegistrationForm;
import jakarta.validation.constraints.NotNull;

public class OfficerCreateForm extends RegistrationForm {
    @NotNull(message = "{validation.departmentId.required}")
    private Long departmentId;

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
}
