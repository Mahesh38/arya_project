package com.smartgrievance.complaint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public class ComplaintForm {
    @NotNull(message = "{validation.categoryId.required}")
    private Long categoryId;

    @NotNull(message = "{validation.areaId.required}")
    private Long areaId;

    @NotBlank(message = "{validation.title.required}")
    @Size(min = 5, max = 100, message = "{validation.title.size}")
    private String title;

    @NotBlank(message = "{validation.description.required}")
    @Size(min = 20, max = 2000, message = "{validation.description.size}")
    private String description;

    @NotBlank(message = "{validation.address.required}")
    @Size(min = 5, max = 255, message = "{validation.address.size}")
    private String address;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    // The multipart files are bound separately. This field exposes only validation errors to the view.
    public List<?> getFiles() { return List.of(); }
}
