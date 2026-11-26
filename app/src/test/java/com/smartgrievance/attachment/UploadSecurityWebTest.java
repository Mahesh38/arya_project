package com.smartgrievance.attachment;

import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.testsupport.WithAppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithAppUser("mahesh@example.com")
class UploadSecurityWebTest extends WebTestBase {
    private static final String CREATE = "/citizen/complaints";
    private static final String TITLE = "Missed roadside collection";
    @Autowired DemoData demo;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void seedCitizen() {
        demo.seedUsers();
    }

    @Test
    @DisplayName("TC-SEC-39, TC-UC-10c and TC-SC-12 reject unsupported content and named empty files")
    void rejectsUnsupportedContent() throws Exception {
        var before = counts();
        for (String name : new String[]{"html-renamed.jpg", "image.svg", "zip-renamed.pdf", "anim.gif", "empty.png", "exe-renamed.jpg"}) {
            assertThat(errors(CREATE, Map.of(), fixture(name, name)))
                    .contains(name + ": only JPG, PNG or PDF files are allowed.");
        }
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("TC-SEC-40 stores only a cleaned original name and a server-generated filename")
    void storesCleanNames() throws Exception {
        submit(CREATE, Map.of(), fixture("photo.png", "folder/field\"<test>.png"))
                .andExpect(status().isFound());
        var row = jdbc.sql("""
                SELECT a.original_name, a.stored_name, a.content_type FROM attachment a
                JOIN complaint c ON c.id = a.complaint_id WHERE c.title = :title
                """).param("title", TITLE).query().singleRow();
        assertThat(row.get("original_name")).isEqualTo("fieldtest.png");
        assertThat((String) row.get("stored_name")).matches("\\d{4}/\\d{2}/[a-f0-9-]{36}\\.png");
        assertThat(row.get("content_type")).isEqualTo("image/png");
    }

    @Test
    @DisplayName("TC-VE-09 shows the count error first, retains file-specific errors and ignores empty controls")
    void reportsEveryFileProblem() throws Exception {
        var before = counts();
        var png = fixture("photo.png", "photo.png");
        var tooLarge = new MockMultipartFile("files", "folder/large.png", "image/png", new byte[6 * 1024 * 1024]);
        var empty = new MockMultipartFile("files", "folder/empty.png", "image/png", new byte[0]);
        var noSelection = new MockMultipartFile("files", "", "application/octet-stream", new byte[0]);
        assertThat(errors(CREATE, Map.of(), png, png, png, png, tooLarge, fixture("exe-renamed.jpg", "folder/invalid.jpg"),
                empty, noSelection)).contains("(you selected 7). large.png is larger than 5 MB. "
                + "invalid.jpg: only JPG, PNG or PDF files are allowed. empty.png: only JPG, PNG or PDF files are allowed.")
                .doesNotContain("folder/");
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("TC-SEC-42 enforces the five-file limit for creation and edit")
    void enforcesPostEditFileCount() throws Exception {
        var png = fixture("photo.png", "photo.png");
        var before = counts();
        assertThat(errors(CREATE, Map.of(), png, png, png, png, png, png)).contains("(you selected 6).");
        assertThat(counts()).isEqualTo(before);
        var created = submit(CREATE, Map.of(), png, png, png, png, png).andExpect(status().isFound()).andReturn();
        String edit = created.getResponse().getRedirectedUrl().replace("/submitted", "/edit");
        before = counts();
        assertThat(errors(edit, Map.of("version", "0"), png)).contains("It has 5 and you selected 1 more.");
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @DisplayName("TC-SEC-46 complaint mutations ignore fields outside the form contract")
    void ignoresUnknownFields() throws Exception {
        var overrides = Map.of("ownerId", "1", "status", "RESOLVED", "departmentId", "1",
                "duplicateOfId", "1", "reopenCount", "9", "complaintNo", "GRV-2000-000001");
        var created = submit(CREATE, overrides).andExpect(status().isFound()).andReturn();
        String submitted = created.getResponse().getRedirectedUrl();
        String number = submitted.split("/")[3];
        var edit = new java.util.HashMap<>(overrides);
        edit.put("version", "0");
        submit(submitted.replace("/submitted", "/edit"), edit).andExpect(status().isFound());
        var row = jdbc.sql("""
                SELECT c.status, c.department_id, c.duplicate_of_id, c.reopen_count, u.email, c.version
                FROM complaint c JOIN app_user u ON c.owner_id = u.id WHERE c.complaint_no = :number
                """).param("number", number).query().singleRow();
        assertThat(number).isNotEqualTo(overrides.get("complaintNo"));
        assertThat(row).containsEntry("status", "SUBMITTED").containsEntry("email", "mahesh@example.com")
                .containsEntry("reopen_count", 0).containsEntry("version", 0)
                .containsEntry("department_id", null).containsEntry("duplicate_of_id", null);
    }

    private String errors(String path, Map<String, String> extra, MockMultipartFile... files) throws Exception {
        var result = submit(path, extra, files).andExpect(status().isOk()).andReturn();
        var html = Html.of(result.getResponse().getContentAsString());
        assertThat(html.document().selectFirst("#title").val()).isEqualTo(TITLE);
        return html.text("#files-error");
    }

    private org.springframework.test.web.servlet.ResultActions submit(String path, Map<String, String> extra,
                                                                      MockMultipartFile... files) throws Exception {
        var request = multipart(path);
        for (var file : files) request.file(file);
        extra.forEach(request::param);
        return mvc.perform(request.with(csrf()).param("title", TITLE)
                .param("description", "Collection has been missed repeatedly at the roadside pickup point.")
                .param("address", "Near the Kothrud ward office").param("categoryId", id("category", "Garbage"))
                .param("areaId", id("area", "Kothrud")));
    }

    private String id(String table, String name) {
        return jdbc.sql("SELECT id FROM " + table + " WHERE name = :name").param("name", name).query(Long.class).single().toString();
    }

    private java.util.List<Long> counts() {
        return java.util.stream.Stream.of("complaint", "attachment", "complaint_event", "notification")
                .map(table -> jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single()).toList();
    }

    private static MockMultipartFile fixture(String file, String name) throws IOException {
        var bytes = new ClassPathResource("files/" + file).getContentAsByteArray();
        return new MockMultipartFile("files", name, "application/octet-stream", bytes);
    }
}
