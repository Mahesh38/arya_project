package com.smartgrievance.attachment;

import ch.qos.logback.classic.Level;
import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.LogCapture;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;

import static com.smartgrievance.testsupport.Logins.appUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttachmentDownloadWebTest extends WebTestBase {
    private static final String OWNER = "sneha@example.com";
    private static final String OFFICER = "suresh.patil@example.com";
    @RegisterExtension final LogCapture logs = new LogCapture();
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData demo;
    @Autowired AttachmentService attachments;
    @Autowired AppUserDetailsService identities;
    @Autowired JdbcClient jdbc;
    long complaintId;
    long ownerId;

    @BeforeEach
    void complaint() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        demo.seed();
        complaintId = jdbc.sql("SELECT id FROM complaint WHERE complaint_no = 'GRV-2026-000003'")
                .query(Long.class).single();
        ownerId = identities.loadUserByUsername(OWNER).id();
        jdbc.sql("""
                UPDATE complaint SET department_id = (SELECT id FROM department WHERE name = 'Sanitation'), status = 'ASSIGNED'
                WHERE id = :id
                """).param("id", complaintId).update();
    }

    @Test
    @DisplayName("TC-RT-73, TC-SEC-41, TC-UC-16a streams exact bytes with private download headers")
    void streamsWithPrivateHeaders() throws Exception {
        var png = fixture("photo.png", "ward photo.png");
        var pdf = fixture("doc.pdf", "फोटो.pdf");
        var files = attachments.add(complaintId, ownerId, AttachmentPurpose.COMPLAINT, List.of(png, pdf));
        String imageUrl = "/attachments/" + files.getFirst().id();
        mvc.perform(get(imageUrl).with(appUser(identities, OWNER)).header("Range", "bytes=0-3"))
                .andExpect(status().isOk()).andExpect(content().bytes(png.getBytes()))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Length", Long.toString(png.getSize())))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"ward photo.png\"; filename*=UTF-8''ward%20photo.png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"));
        mvc.perform(get("/attachments/" + files.getLast().id()).param("download", "true").with(appUser(identities, OFFICER)))
                .andExpect(status().isOk()).andExpect(content().bytes(pdf.getBytes()))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"____.pdf\"; filename*=UTF-8''%E0%A4%AB%E0%A5%8B%E0%A4%9F%E0%A5%8B.pdf"));
        mvc.perform(get(imageUrl).param("download", "maybe").with(appUser(identities, OWNER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-BR-09, TC-UC-16c only an admin can stream a soft-deleted attachment")
    void deletedAttachmentIsRestrictedToAdmins() throws Exception {
        var file = attachments.active(complaintId, AttachmentPurpose.COMPLAINT).getFirst();
        attachments.remove(complaintId, ownerId, List.of(file.id()));
        String url = "/attachments/" + file.id();
        for (String email : List.of(OWNER, OFFICER, "mahesh@example.com")) {
            mvc.perform(get(url).with(appUser(identities, email))).andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));
        }
        mvc.perform(get(url).with(appUser(identities, properties.admin().email())))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        assertThat(attachments.visible(complaintId, AttachmentPurpose.COMPLAINT, true))
                .anyMatch(view -> view.id() == file.id() && view.deleted() && view.deletedAt() != null);
    }

    @Test
    @DisplayName("TC-BR-17 download visibility follows the complaint for each role and relation")
    void attachmentAccessMatchesParentComplaint() throws Exception {
        long original = jdbc.sql("SELECT id FROM complaint WHERE complaint_no = 'GRV-2026-000001'")
                .query(Long.class).single();
        long complainant = identities.loadUserByUsername("mahesh@example.com").id();
        var files = attachments.add(original, complainant, AttachmentPurpose.COMPLAINT,
                List.of(fixture("photo.png", "ward.png")));
        String link = "/attachments/" + files.getFirst().id();
        for (String email : List.of("mahesh@example.com", "jayesh@example.com", "anita.deshmukh@example.com", properties.admin().email())) {
            mvc.perform(get(link).with(appUser(identities, email))).andExpect(status().isOk());
        }
        for (String email : List.of(OWNER, OFFICER)) {
            mvc.perform(get(link).with(appUser(identities, email))).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("TC-SEC-23 a live file on a hidden complaint has the same response as an unknown file")
    void hiddenAndUnknownAttachmentsHaveTheSamePublicResponse() throws Exception {
        var file = attachments.active(complaintId, AttachmentPurpose.COMPLAINT).getFirst();
        var stranger = appUser(identities, "mahesh@example.com");
        var hidden = mvc.perform(get("/attachments/" + file.id()).with(stranger)).andExpect(status().isNotFound())
                .andExpect(view().name("error/404")).andReturn();
        var unknown = mvc.perform(get("/attachments/999999").with(stranger)).andExpect(status().isNotFound())
                .andExpect(view().name("error/404")).andReturn();
        var hiddenPage = Html.of(hidden.getResponse().getContentAsString());
        var unknownPage = Html.of(unknown.getResponse().getContentAsString());
        assertThat(hiddenPage.text("main")).isEqualTo(unknownPage.text("main"));
    }

    @Test
    @DisplayName("TC-UC-16b missing storage yields the same 404 and one error for the attachment")
    void missingFileHasNoPublicDiagnostic() throws Exception {
        var file = attachments.active(complaintId, AttachmentPurpose.COMPLAINT).getFirst();
        String name = jdbc.sql("SELECT stored_name FROM attachment WHERE id = :id").param("id", file.id())
                .query(String.class).single();
        Files.delete(properties.upload().dir().resolve(name));
        mvc.perform(get("/attachments/" + file.id()).with(appUser(identities, OWNER)))
                .andExpect(status().isNotFound()).andExpect(view().name("error/404"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(name))));
        assertThat(logs.entries().stream().filter(event -> event.getLevel() == Level.ERROR)
                .map(event -> event.getFormattedMessage())).singleElement().asString()
                .startsWith("ATTACHMENT_UNAVAILABLE id=" + file.id() + " ");
    }

    private static MockMultipartFile fixture(String resource, String name) throws Exception {
        return new MockMultipartFile("files", name, "application/octet-stream",
                new ClassPathResource("files/" + resource).getContentAsByteArray());
    }
}
