package com.smartgrievance.attachment;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.smartgrievance.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {
    private final AttachmentRepository data;
    private final FileStorageService storage;
    private final Clock clock;

    AttachmentService(AttachmentRepository data, FileStorageService storage, Clock clock) {
        this.data = data;
        this.storage = storage;
        this.clock = clock;
    }

    public List<AttachmentView> active(long complaintId, AttachmentPurpose purpose) {
        return data.active(complaintId, purpose);
    }

    @Transactional
    public List<AttachmentView> add(long complaintId, long actorId, AttachmentPurpose purpose, List<MultipartFile> parts) {
        var now = LocalDateTime.now(clock);
        return storage.store(parts).stream().map(file -> data.insert(complaintId, actorId, purpose, now, file)).toList();
    }

    @Transactional
    public List<AttachmentView> remove(long complaintId, long actorId, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        var unique = Set.copyOf(ids);
        var removed = active(complaintId, AttachmentPurpose.COMPLAINT).stream()
                .filter(file -> unique.contains(file.id())).toList();
        if (removed.size() != unique.size()) throw missing(complaintId);
        var now = LocalDateTime.now(clock);
        if (data.remove(complaintId, actorId, removed.stream().map(AttachmentView::id).toList(), now) != removed.size()) {
            throw missing(complaintId);
        }
        return removed.stream().map(file -> new AttachmentView(file.id(), file.originalName(), file.contentType(),
                file.image(), file.sizeLabel(), file.purpose(), file.uploadedAt(), true, now)).toList();
    }

    private static NotFoundException missing(long complaintId) {
        return new NotFoundException("attachment", Long.toString(complaintId), true);
    }
}
