package com.smartgrievance.attachment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class AttachmentRepository {
    private final JdbcClient jdbc;

    AttachmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Download(long complaintId, long ownerId, Long departmentId, String name, String storedName,
                    String contentType, long size, boolean deleted) {}

    List<AttachmentView> visible(long complaintId, AttachmentPurpose purpose, boolean includeDeleted) {
        return jdbc.sql("""
                SELECT id, original_name, content_type, size_bytes, uploaded_at, deleted, deleted_at
                FROM attachment WHERE complaint_id = :complaintId AND purpose = :purpose
                  AND (:includeDeleted = TRUE OR deleted = FALSE)
                ORDER BY id
                """).param("complaintId", complaintId).param("purpose", purpose.name())
                .param("includeDeleted", includeDeleted)
                .query((row, index) -> AttachmentView.of(row.getLong("id"), row.getString("original_name"),
                        row.getString("content_type"), row.getLong("size_bytes"), purpose,
                        row.getObject("uploaded_at", LocalDateTime.class), row.getBoolean("deleted"),
                        row.getObject("deleted_at", LocalDateTime.class))).list();
    }

    Optional<Download> download(long id) {
        return jdbc.sql("""
                SELECT a.complaint_id, c.owner_id, c.department_id, a.original_name, a.stored_name,
                       a.content_type, a.size_bytes, a.deleted
                FROM attachment a JOIN complaint c ON c.id = a.complaint_id WHERE a.id = :id
                """).param("id", id).query((row, index) -> new Download(row.getLong("complaint_id"),
                        row.getLong("owner_id"), row.getObject("department_id", Long.class),
                        row.getString("original_name"), row.getString("stored_name"), row.getString("content_type"),
                        row.getLong("size_bytes"), row.getBoolean("deleted"))).optional();
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
