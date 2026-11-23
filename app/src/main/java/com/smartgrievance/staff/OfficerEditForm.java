package com.smartgrievance.staff;

import com.smartgrievance.common.validation.ValidMobile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class OfficerEditForm {
    @NotNull(message = "{validation.version.required}")
    private Integer version;
    @NotBlank(message = "{validation.fullName.required}")
    @Size(min = 2, max = 100, message = "{validation.fullName.size}")
    @Pattern(regexp = "^[\\p{L}\\p{M} .'’-]*$", message = "{validation.fullName.chars}")
    private String fullName;
    @NotBlank(message = "{validation.mobile.required}")
    @ValidMobile(message = "{validation.mobile.format}")
    private String mobile;
    @NotNull(message = "{validation.departmentId.required}")
    private Long departmentId;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
}
