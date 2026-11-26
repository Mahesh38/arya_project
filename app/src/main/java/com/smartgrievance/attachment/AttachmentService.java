package com.smartgrievance.attachment;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.smartgrievance.access.ComplaintAccessPolicy;
import com.smartgrievance.common.exception.NotFoundException;
import com.smartgrievance.complaint.ComplaintVisibility;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {
    private static final Logger LOG = LoggerFactory.getLogger(AttachmentService.class);
    private final AttachmentRepository data;
    private final FileStorageService storage;
    private final Clock clock;
    private final ComplaintVisibility visibility;

    AttachmentService(AttachmentRepository data, FileStorageService storage, Clock clock, ComplaintVisibility visibility) {
        this.data = data;
        this.storage = storage;
        this.clock = clock;
        this.visibility = visibility;
    }

    public List<AttachmentView> active(long complaintId, AttachmentPurpose purpose) {
        return visible(complaintId, purpose, false);
    }

    public List<AttachmentView> visible(long complaintId, AttachmentPurpose purpose, boolean includeDeleted) {
        return data.visible(complaintId, purpose, includeDeleted);
    }

    @Transactional(readOnly = true)
    public AttachmentContent load(long id, CurrentUser viewer) {
        var file = data.download(id).orElseThrow(() -> missing(id));
        var relation = visibility.relation(viewer, file.complaintId(), file.ownerId(), file.departmentId());
        if (!ComplaintAccessPolicy.canView(relation) || file.deleted() && viewer.role() != Role.ADMIN) {
            throw missing(id);
        }
        try {
            var path = storage.resolveForRead(file.storedName());
            return new AttachmentContent(file.name(), file.contentType(), file.size(),
                    new InputStreamResource(Files.newInputStream(path)));
        } catch (IOException failure) {
            LOG.error("ATTACHMENT_UNAVAILABLE id={} complaintId={} reason={}", id, file.complaintId(),
                    failure.getClass().getSimpleName());
            throw missing(id);
        }
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

    private static NotFoundException missing(long id) {
        return new NotFoundException("attachment", Long.toString(id), true);
    }
}
