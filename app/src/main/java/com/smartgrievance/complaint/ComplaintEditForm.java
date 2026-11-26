package com.smartgrievance.complaint;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ComplaintEditForm extends ComplaintForm {
    @NotNull(message = "{validation.version.required}")
    private Integer version;

    @Size(max = 500, message = "{validation.optionalText.size}")
    private String changeReason;

    private List<Long> removeAttachmentIds = List.of();

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public List<Long> getRemoveAttachmentIds() { return removeAttachmentIds; }
    public void setRemoveAttachmentIds(List<Long> ids) { removeAttachmentIds = ids == null ? List.of() : ids; }
}
