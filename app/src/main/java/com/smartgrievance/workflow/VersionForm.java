package com.smartgrievance.workflow;

import jakarta.validation.constraints.NotNull;

public class VersionForm {
    @NotNull(message = "{validation.version.required}")
    private Integer version;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
