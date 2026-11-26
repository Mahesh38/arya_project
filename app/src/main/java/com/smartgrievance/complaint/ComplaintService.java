package com.smartgrievance.complaint;

import com.smartgrievance.access.ComplaintAccessPolicy;
import com.smartgrievance.access.ComplaintAccessPolicy.Relation;
import com.smartgrievance.attachment.AttachmentPurpose;
import com.smartgrievance.attachment.AttachmentService;
import com.smartgrievance.attachment.AttachmentView;
import com.smartgrievance.attachment.FileStorageService;
import com.smartgrievance.common.exception.BusinessRuleException;
import com.smartgrievance.common.exception.ConcurrentUpdateException;
import com.smartgrievance.common.exception.FormError;
import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.exception.NotFoundException;
import com.smartgrievance.common.exception.Subject;
import com.smartgrievance.complaint.ComplaintRepository.Complaint;
import com.smartgrievance.complaint.ComplaintRepository.Lookup;
import com.smartgrievance.config.AppProperties;
import com.smartgrievance.history.ComplaintHistory;
import com.smartgrievance.history.ComplaintHistory.Event;
import com.smartgrievance.history.ComplaintHistory.Revision;
import com.smartgrievance.masterdata.LookupOption;
import com.smartgrievance.notification.NotificationService;
import com.smartgrievance.security.SecurityEvents;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.user.Role;
import com.smartgrievance.workflow.ActorContext;
import com.smartgrievance.workflow.ComplaintFacts;
import com.smartgrievance.workflow.TransitionRules;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ComplaintService {
    private static final Logger LOG = LoggerFactory.getLogger(ComplaintService.class);
    private static final AttachmentPurpose FILE_PURPOSE = AttachmentPurpose.COMPLAINT;

    public record EditPage(ComplaintSummary complaint, ComplaintEditForm form, List<AttachmentView> files,
                           long categoryId, long areaId) {}

    private final ComplaintRepository repository;
    private final ComplaintVisibility visibility;
    private final ComplaintNumberGenerator numbers;
    private final AttachmentService attachments;
    private final FileStorageService files;
    private final ComplaintHistory history;
    private final NotificationService notifications;
    private final AppProperties.Complaint config;
    private final Clock clock;

    public ComplaintService(ComplaintRepository repository, ComplaintNumberGenerator numbers,
                            AttachmentService attachments, FileStorageService files,
                            ComplaintHistory history, NotificationService notifications,
                            AppProperties properties, Clock clock, ComplaintVisibility visibility) {
        this.repository = repository;
        this.visibility = visibility;
        this.numbers = numbers;
        this.attachments = attachments;
        this.files = files;
        this.history = history;
        this.notifications = notifications;
        this.config = properties.complaint();
        this.clock = clock;
    }

    @Transactional
    public String submit(ComplaintForm form, List<MultipartFile> uploaded, CurrentUser user) {
        if (user.role() != Role.CITIZEN || !user.mobileVerified() || !repository.lockVerifiedOwner(user.id())) {
            throw new BusinessRuleException("BR-86", "flash.otp.verifyFirst", new Subject(Subject.Type.OTP));
        }
        reject(files.validate(uploaded, FILE_PURPOSE, 0, 0, user.id()));
        var now = now();
        if (repository.filedSince(user.id(), now.minusHours(24)) >= config.maxPerDay()) {
            reject(List.of(new FormError(null, "validation.complaint.dailyLimit", config.maxPerDay())));
        }
        validateLookups(form, null);
        int year = now.getYear();
        String number = ComplaintNumberGenerator.format(year, numbers.next(year));
        long id = repository.insert(number, user.id(), form, now);
        attachments.add(id, user.id(), FILE_PURPOSE, uploaded);
        long event = history.record(id, Event.CREATED, null, ComplaintStatus.SUBMITTED, user.id(), null, null, null, now);
        notifications.submitted(id, event, user.id(), number, form.getTitle(), now);
        LOG.info("COMPLAINT_TRANSITION complaintNo={} from={} to={} actorId={}", number, null, ComplaintStatus.SUBMITTED, user.id());
        return number;
    }

    @Transactional(readOnly = true)
    public ComplaintSummary submitted(String number, CurrentUser user) {
        var complaint = load(number, false);
        if (user.role() != Role.CITIZEN || complaint.ownerId() != user.id()) throw missing(number, true);
        return summary(complaint);
    }

    @Transactional(readOnly = true)
    public EditPage editPage(String number, CurrentUser user) {
        var complaint = forAction(number, user, Action.EDIT, false);
        var form = new ComplaintEditForm();
        form.setVersion(complaint.version());
        form.setCategoryId(complaint.categoryId());
        form.setAreaId(complaint.areaId());
        form.setTitle(complaint.title());
        form.setDescription(complaint.description());
        form.setAddress(complaint.address());
        return new EditPage(summary(complaint), form, attachments.active(complaint.id(), FILE_PURPOSE),
                complaint.categoryId(), complaint.areaId());
    }

    @Transactional
    public boolean edit(String number, ComplaintEditForm form, List<MultipartFile> uploaded, CurrentUser user) {
        var complaint = forAction(number, user, Action.EDIT, true);
        checkVersion(complaint, form.getVersion());
        var current = attachments.active(complaint.id(), FILE_PURPOSE);
        var activeIds = current.stream().map(AttachmentView::id).collect(Collectors.toSet());
        var removals = new ArrayList<Long>();
        for (Long id : form.getRemoveAttachmentIds()) {
            if (!activeIds.contains(id)) {
                SecurityEvents.LOG.warn("ATTACHMENT_REMOVE_IGNORED userId={} complaintNo={}", user.id(), number);
            } else if (!removals.contains(id)) {
                removals.add(id);
            }
        }
        reject(files.validateEdit(uploaded, FILE_PURPOSE, current.size(), removals.size(), user.id()));
        var choices = validateLookups(form, complaint);
        var now = now();
        var revisions = new ArrayList<Revision>();
        if (form.getCategoryId() != complaint.categoryId()) {
            revisions.add(new Revision("category", complaint.categoryName(), choices.category().name()));
        }
        if (form.getAreaId() != complaint.areaId()) {
            revisions.add(new Revision("area", complaint.areaName(), choices.area().name()));
        }
        change(revisions, "title", complaint.title(), form.getTitle());
        change(revisions, "description", complaint.description(), form.getDescription());
        change(revisions, "address", complaint.address(), form.getAddress());
        attachments.remove(complaint.id(), user.id(), removals)
                .forEach(file -> revisions.add(new Revision("attachment", file.originalName(), null)));
        attachments.add(complaint.id(), user.id(), FILE_PURPOSE, uploaded)
                .forEach(file -> revisions.add(new Revision("attachment", null, file.originalName())));
        if (revisions.isEmpty()) return false;
        if (!repository.edit(complaint.id(), form, now)) throw concurrent(complaint);
        long event = history.record(complaint.id(), Event.EDITED, null, null, user.id(), null, null,
                form.getChangeReason(), now);
        history.revisions(event, revisions);
        notifications.edited(complaint.id(), event, number, now);
        return true;
    }

    @Transactional(readOnly = true)
    public ComplaintSummary withdrawal(String number, CurrentUser user) {
        return summary(forAction(number, user, Action.WITHDRAW, false));
    }

    @Transactional
    public void withdraw(String number, RemarkForm form, CurrentUser user) {
        var complaint = forAction(number, user, Action.WITHDRAW, true);
        checkVersion(complaint, form.getVersion());
        var now = now();
        if (!repository.updateStatus(complaint.id(), complaint.version(), complaint.status(), ComplaintStatus.WITHDRAWN, now)) {
            throw concurrent(complaint);
        }
        long event = history.record(complaint.id(), Event.STATUS_CHANGED, complaint.status(),
                ComplaintStatus.WITHDRAWN, user.id(), complaint.departmentId(), null, form.getRemark(), now);
        notifications.withdrawn(complaint.id(), event, complaint.departmentId(), number, form.getRemark(), now);
        LOG.info("COMPLAINT_TRANSITION complaintNo={} from={} to={} actorId={}",
                number, complaint.status(), ComplaintStatus.WITHDRAWN, user.id());
    }

    private Complaint forAction(String number, CurrentUser user, Action action, boolean lock) {
        var complaint = load(number, lock);
        var actor = ActorContext.of(user);
        var facts = visibleFacts(complaint, actor);
        var decision = TransitionRules.decide(action, actor, facts, now(), config.reviewWindowDays(), config.maxReopens());
        if (!decision.allowed() && "error.withdraw.linkedDuplicates".equals(decision.refusal())) {
            throw refused(complaint, "BR-47", decision.refusal());
        }
        TransitionRules.require(action, actor, facts, now(), config.reviewWindowDays(), config.maxReopens(),
                subject(complaint));
        return complaint;
    }

    private ComplaintFacts visibleFacts(Complaint complaint, ActorContext actor) {
        var access = visibility.access(actor, complaint.id(), complaint.ownerId(), complaint.departmentId());
        var relation = access.relation();
        if (!ComplaintAccessPolicy.canView(relation)) throw missing(complaint.number(), true);
        boolean hasDuplicates = TransitionRules.allows(Action.WITHDRAW, complaint.status(), relation)
                && repository.hasDuplicates(complaint.id());
        return new ComplaintFacts(complaint.status(), complaint.ownerId(), complaint.departmentId(),
                complaint.reopenCount(), complaint.resolvedAt(), hasDuplicates, access.watching(), access.linked());
    }

    private record Choices(LookupOption category, LookupOption area) {}

    private Choices validateLookups(ComplaintForm form, Complaint previous) {
        var category = repository.lookup(Lookup.CATEGORY, form.getCategoryId(), true).orElse(null);
        var area = repository.lookup(Lookup.AREA, form.getAreaId(), true).orElse(null);
        var errors = new ArrayList<FormError>();
        if (category == null || !category.active() && (previous == null || category.id() != previous.categoryId())) {
            errors.add(new FormError("categoryId", "validation.categoryId.inactive"));
        }
        if (area == null || !area.active() && (previous == null || area.id() != previous.areaId())) {
            errors.add(new FormError("areaId", "validation.areaId.inactive"));
        }
        reject(errors);
        return new Choices(category, area);
    }

    private Complaint load(String number, boolean lock) {
        return repository.find(number, lock).orElseThrow(() -> missing(number, false));
    }

    private ComplaintSummary summary(Complaint c) {
        return new ComplaintSummary(c.number(), c.title(), c.status(), c.categoryName(), c.areaName(), c.departmentName(),
                c.version(), c.resolvedAt() == null ? null : c.resolvedAt().plusDays(config.reviewWindowDays()));
    }

    private static void change(List<Revision> revisions, String field, String oldValue, String newValue) {
        if (!Objects.equals(oldValue, newValue)) revisions.add(new Revision(field, oldValue, newValue));
    }

    private static void reject(List<FormError> errors) {
        if (!errors.isEmpty()) throw new FormRejectedException(errors);
    }

    private static void checkVersion(Complaint complaint, Integer version) {
        if (!Objects.equals(complaint.version(), version)) throw concurrent(complaint);
    }

    private static ConcurrentUpdateException concurrent(Complaint complaint) {
        return new ConcurrentUpdateException("error.concurrent.complaint", subject(complaint));
    }

    private static BusinessRuleException refused(Complaint c, String rule, String key, Object... args) {
        return new BusinessRuleException(rule, key, subject(c), args);
    }

    private static Subject subject(Complaint complaint) { return new Subject(Subject.Type.COMPLAINT, complaint.number()); }
    private static NotFoundException missing(String number, boolean hidden) { return new NotFoundException("complaint", number, hidden); }
    private LocalDateTime now() { return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS); }
}
