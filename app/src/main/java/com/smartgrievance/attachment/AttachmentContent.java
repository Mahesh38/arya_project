package com.smartgrievance.attachment;

import org.springframework.core.io.InputStreamResource;

public record AttachmentContent(String originalName, String contentType, long sizeBytes, InputStreamResource body) {}
