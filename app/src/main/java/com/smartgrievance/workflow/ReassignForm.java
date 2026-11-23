package com.smartgrievance.workflow;

import com.smartgrievance.complaint.RemarkForm;
import jakarta.validation.constraints.NotNull;

public class ReassignForm extends RemarkForm {
    @NotNull(message = "{validation.departmentId.required}")
    private Long departmentId;

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
}
