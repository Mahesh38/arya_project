package com.smartgrievance.seed;

import com.smartgrievance.attachment.AttachmentPurpose;
import com.smartgrievance.attachment.AttachmentService;
import com.smartgrievance.common.exception.StorageException;
import com.smartgrievance.complaint.ComplaintForm;
import com.smartgrievance.complaint.ComplaintNumberGenerator;
import com.smartgrievance.complaint.ComplaintRepository;
import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.history.ComplaintHistory;
import com.smartgrievance.history.ComplaintHistory.Event;
import com.smartgrievance.notification.NotificationService;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import static com.smartgrievance.complaint.ComplaintStatus.*;

@Component
public class ComplaintDemoData {
    private static final String WATER = "Water Supply";
    private static final String ELECTRICAL = "Electrical";
    private static final String ROADS = "Road Maintenance";
    private static final String DRAINAGE = "Drainage";
    private static final String SANITATION = "Sanitation";
    private static final String WATER_CATEGORY = "Water Leakage";
    private static final String STREETLIGHT = "Streetlight";

    private record Sample(String owner, String category, String area, String department, String title,
                          ComplaintStatus status, int age, int resolvedAge) {}

    private static final List<Sample> SAMPLES = List.of(
            new Sample("mahesh", WATER_CATEGORY, "Kothrud", WATER, "Pipeline leaking on Karve Road near Kothrud Depot", IN_PROGRESS, 1, 0),
            new Sample("jayesh", WATER_CATEGORY, "Kothrud", WATER, "Water leaking near Kothrud Depot", DUPLICATE, 1, 0),
            new Sample("sneha", "Garbage", "Hadapsar", null, "Garbage not collected for 4 days near Magarpatta gate", SUBMITTED, 4, 0),
            new Sample("mahesh", STREETLIGHT, "Aundh", ELECTRICAL, "Streetlight on ITI Road does not work", RESOLVED, 4, 2),
            new Sample("jayesh", "Road Damage", "Shivajinagar", ROADS, "Large pothole on FC Road", ASSIGNED, 15, 0),
            new Sample("sneha", DRAINAGE, "Kasba Peth", DRAINAGE, "Drainage blocked near the market", CLOSED, 6, 2),
            new Sample("jayesh", "Garbage", "Viman Nagar", SANITATION, "Garbage collected once but piled up again", REOPENED, 6, 0),
            new Sample("sneha", "Other", "Baner", null, "Illegal hoarding blocking footpath on Baner Road", UNDER_REVIEW, 2, 0),
            new Sample("mahesh", "Road Damage", "Baner", ROADS, "Road is on a private society premises, outside PMC scope", REJECTED, 3, 0),
            new Sample("sneha", STREETLIGHT, "Kasba Peth", null, "Light started working again", WITHDRAWN, 2, 0),
            new Sample("mahesh", STREETLIGHT, "Viman Nagar", ELECTRICAL, "Streetlight flickering near the main gate", RESOLVED, 15, 8));

    private final JdbcClient jdbc;
    private final ComplaintRepository complaints;
    private final ComplaintNumberGenerator numbers;
    private final ComplaintHistory history;
    private final NotificationService notifications;
    private final AttachmentService attachments;
    private final Clock clock;
    private final com.smartgrievance.config.AppProperties.Admin adminProperties;

    ComplaintDemoData(JdbcClient jdbc, ComplaintRepository complaints, ComplaintNumberGenerator numbers, ComplaintHistory history,
                      NotificationService notifications, AttachmentService attachments, Clock clock,
                      com.smartgrievance.config.AppProperties properties) {
        this.jdbc = jdbc;
        this.complaints = complaints;
        this.numbers = numbers;
        this.history = history;
        this.notifications = notifications;
        this.attachments = attachments;
        this.clock = clock;
        adminProperties = properties.admin();
    }

    @Transactional
    public void seed() {
        if (jdbc.sql("SELECT COUNT(*) FROM complaint").query(Long.class).single() > 0) return;
        var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        long admin = userId(adminProperties.email());
        long first = 0;
        for (int index = 0; index < SAMPLES.size(); index++) {
            Sample sample = SAMPLES.get(index);
            long owner = userId(sample.owner() + "@example.com");
            Long department = sample.department() == null ? null : lookup("department", sample.department());
            var form = new ComplaintForm();
            form.setCategoryId(lookup("category", sample.category()));
            form.setAreaId(lookup("area", sample.area()));
            form.setTitle(sample.title());
            form.setDescription(sample.title() + ". Please review the location and take the appropriate action.");
            form.setAddress(sample.area() + ", Pune");
            int year = now.getYear();
            String number = ComplaintNumberGenerator.format(year, numbers.next(year));
            var created = now.minusDays(sample.age());
            long id = complaints.insert(number, owner, form, created);
            if (index == 0) first = id;
            long submitted = history.record(id, Event.CREATED, null, SUBMITTED, owner, null, null, null, created);
            notifications.submitted(id, submitted, owner, number, sample.title(), created);
            var updated = seedHistory(sample, id, first, owner, admin, department, created, now);
            jdbc.sql("""
                    UPDATE complaint SET status = :status, department_id = :dept, duplicate_of_id = :original,
                        reopen_count = :reopens, resolved_at = :resolved, updated_at = :updated, version = :version
                    WHERE id = :id
                    """).param("status", sample.status().name()).param("dept", department)
                    .param("original", sample.status() == DUPLICATE ? first : null)
                    .param("reopens", sample.status() == REOPENED ? 1 : 0)
                    .param("resolved", sample.resolvedAge() == 0 ? null : now.minusDays(sample.resolvedAge()))
                    .param("updated", updated).param("version", sample.status() == SUBMITTED ? 0 : 1)
                    .param("id", id).update();
            if (index == 2) {
                attachments.add(id, owner, AttachmentPurpose.COMPLAINT,
                        List.of(photo("garbage-magarpatta-1.jpg"), photo("garbage-magarpatta-2.jpg")));
            }
        }
    }

    private LocalDateTime seedHistory(Sample sample, long id, long first, long owner, long admin,
                                      Long department, LocalDateTime created, LocalDateTime now) {
        var status = sample.status();
        if (status == SUBMITTED) return created;
        if (status == WITHDRAWN) {
            return transition(id, SUBMITTED, WITHDRAWN, owner, null, "Light started working again", created.plusHours(2));
        }
        var time = transition(id, SUBMITTED, UNDER_REVIEW, admin, null, null, created.plusHours(1));
        if (status == UNDER_REVIEW) {
            time = time.plusHours(1);
            long edited = history.record(id, Event.EDITED, null, null, owner, null, null, "Clarified the location", time);
            history.revisions(edited, List.of(new ComplaintHistory.Revision("title", "Hoarding blocking footpath", sample.title())));
            return time;
        }
        time = transition(id, UNDER_REVIEW, ASSIGNED, admin, department, null, time.plusHours(1));
        if (status == ASSIGNED) return time;
        if (status == REJECTED) {
            return transition(id, ASSIGNED, REJECTED, admin, department, "Outside PMC scope", time.plusHours(1));
        }
        long officer = officerId(department);
        if (status == DUPLICATE) {
            time = time.plusHours(1);
            history.record(id, Event.DUPLICATE_LINKED, ASSIGNED, DUPLICATE, officer, department, first, "Same leak", time);
            history.record(first, Event.DUPLICATE_RECEIVED, null, null, officer, department, id, "Same leak", time);
            return time;
        }
        time = transition(id, ASSIGNED, IN_PROGRESS, officer, department, "Team sent to the site", time.plusHours(1));
        if (status == IN_PROGRESS) return time;
        var resolution = sample.resolvedAge() == 0 ? time.plusHours(1) : now.minusDays(sample.resolvedAge());
        time = transition(id, IN_PROGRESS, RESOLVED, officer, department, "Work completed", resolution);
        if (status == REOPENED) {
            return transition(id, RESOLVED, REOPENED, owner, department, sample.title(), time.plusHours(1));
        }
        if (status == CLOSED) return transition(id, RESOLVED, CLOSED, owner, department, "Fixed quickly, thanks", time.plusHours(1));
        return time;
    }

    private LocalDateTime transition(long complaint, ComplaintStatus from, ComplaintStatus to, long actor,
                                     Long dept, String remark, LocalDateTime at) {
        history.record(complaint, Event.STATUS_CHANGED, from, to, actor, dept, null, remark, at);
        return at;
    }

    private long userId(String email) {
        return jdbc.sql("SELECT id FROM app_user WHERE email = :email").param("email", email).query(Long.class).single();
    }

    private long lookup(String table, String name) {
        return jdbc.sql("SELECT id FROM " + table + " WHERE name = :name").param("name", name).query(Long.class).single();
    }

    private long officerId(long department) {
        return jdbc.sql("SELECT id FROM app_user WHERE role = 'OFFICER' AND department_id = :id AND active = TRUE ORDER BY id LIMIT 1")
                .param("id", department).query(Long.class).single();
    }

    private static MultipartFile photo(String name) {
        try (var content = new ClassPathResource("seed/files/" + name).getInputStream()) {
            return new SeedPhoto(name, content.readAllBytes());
        } catch (IOException failure) {
            throw new StorageException(failure);
        }
    }

    private record SeedPhoto(String name, byte[] data) implements MultipartFile {
        @Override public String getName() { return "files"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return "image/jpeg"; }
        @Override public boolean isEmpty() { return data.length == 0; }
        @Override public long getSize() { return data.length; }
        @Override public byte[] getBytes() { return data.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(data); }
        @Override public void transferTo(File destination) throws IOException { Files.write(destination.toPath(), data); }
    }
}
