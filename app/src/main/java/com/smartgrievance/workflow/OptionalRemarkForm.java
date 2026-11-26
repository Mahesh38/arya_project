package com.smartgrievance.workflow;

import jakarta.validation.constraints.Size;

public class OptionalRemarkForm extends VersionForm {
    @Size(max = 500, message = "{validation.optionalText.size}")
    private String remark;

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
