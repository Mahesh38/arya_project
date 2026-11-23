package com.smartgrievance.complaint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class RemarkForm {
    @NotNull(message = "{validation.version.required}")
    private Integer version;

    @NotBlank(message = "{validation.remark.required}")
    @Size(min = 10, max = 500, message = "{validation.remark.size}")
    private String remark;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
