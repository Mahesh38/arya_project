package com.smartgrievance.attachment;

import java.time.LocalDateTime;
import java.util.Locale;

public record AttachmentView(long id, String originalName, String contentType, boolean image, String sizeLabel,
                             AttachmentPurpose purpose, LocalDateTime uploadedAt, boolean deleted, LocalDateTime deletedAt) {

    static AttachmentView of(long id, String name, String type, long size, AttachmentPurpose purpose,
                             LocalDateTime uploadedAt, boolean deleted, LocalDateTime deletedAt) {
        return new AttachmentView(id, name, type, type.startsWith("image/"), label(size), purpose, uploadedAt,
                deleted, deletedAt);
    }

    private static String label(long size) {
        if (size >= 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024));
        if (size >= 1024) return Math.round(size / 1024.0) + " KB";
        return size + " B";
    }
}
