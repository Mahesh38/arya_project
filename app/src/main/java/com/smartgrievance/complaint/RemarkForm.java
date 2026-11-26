package com.smartgrievance.complaint;

import jakarta.validation.constraints.NotBlank;
import com.smartgrievance.workflow.VersionForm;
import jakarta.validation.constraints.Size;

public class RemarkForm extends VersionForm {

    @NotBlank(message = "{validation.remark.required}")
    @Size(min = 10, max = 500, message = "{validation.remark.size}")
    private String remark;

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
