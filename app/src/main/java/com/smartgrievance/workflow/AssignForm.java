package com.smartgrievance.workflow;

import jakarta.validation.constraints.NotNull;

public class AssignForm extends OptionalRemarkForm {
    @NotNull(message = "{validation.departmentId.required}")
    private Long departmentId;

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
}
