package com.smartgrievance.attachment;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class AttachmentRepository {
    private final JdbcClient jdbc;

    AttachmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<AttachmentView> active(long complaintId, AttachmentPurpose purpose) {
        return jdbc.sql("""
                SELECT id, original_name, content_type, size_bytes, uploaded_at
                FROM attachment WHERE complaint_id = :complaintId AND purpose = :purpose AND deleted = FALSE
                ORDER BY id
                """).param("complaintId", complaintId).param("purpose", purpose.name())
                .query((row, index) -> AttachmentView.of(row.getLong("id"), row.getString("original_name"),
                        row.getString("content_type"), row.getLong("size_bytes"), purpose,
                        row.getObject("uploaded_at", LocalDateTime.class), false, null)).list();
    }

    AttachmentView insert(long complaintId, long actorId, AttachmentPurpose purpose, LocalDateTime now,
                          FileStorageService.StoredFile file) {
        var key = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO attachment (complaint_id, purpose, original_name, stored_name, content_type, size_bytes,
                                        uploaded_by, uploaded_at)
                VALUES (:complaintId, :purpose, :originalName, :storedName, :contentType, :sizeBytes, :actorId, :now)
                """).param("complaintId", complaintId).param("purpose", purpose.name())
                .param("originalName", file.originalName()).param("storedName", file.storedName())
                .param("contentType", file.contentType()).param("sizeBytes", file.sizeBytes())
                .param("actorId", actorId).param("now", now).update(key, "id");
        return AttachmentView.of(key.getKey().longValue(), file.originalName(), file.contentType(), file.sizeBytes(),
                purpose, now, false, null);
    }

    int remove(long complaintId, long actorId, List<Long> ids, LocalDateTime now) {
        return jdbc.sql("""
                UPDATE attachment SET deleted = TRUE, deleted_by = :actorId, deleted_at = :now
                WHERE complaint_id = :complaintId AND purpose = 'COMPLAINT' AND deleted = FALSE AND id IN (:ids)
                """).param("actorId", actorId).param("now", now).param("complaintId", complaintId)
                .param("ids", ids).update();
    }
}
