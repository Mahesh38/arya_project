package com.smartgrievance.masterdata;

import com.smartgrievance.common.PageResult;
import com.smartgrievance.testsupport.LogCapture;
import com.smartgrievance.testsupport.Logins;
import com.smartgrievance.testsupport.TestDatabase;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.BindingResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MasterDataRouteWebTest extends WebTestBase {
    @Autowired AccountBootstrap bootstrap;
    @Autowired AppUserDetailsService details;
    @Autowired MasterDataService masterData;
    @Autowired JdbcClient jdbc;
    @RegisterExtension final LogCapture logs = new LogCapture();

    @BeforeEach
    void administrator() { bootstrap.ensureAdmin(properties.admin(), false); }

    static Stream<Arguments> masterRoutes() {
        var types = MasterType.values();
        return IntStream.range(0, types.length).boxed().flatMap(index -> IntStream.range(0, 7)
                .mapToObj(operation -> Arguments.of("TC-RT-" + (80 + index * 7 + operation), types[index], operation)));
    }

    @ParameterizedTest(name = "{0} {1}, operation {2}")
    @MethodSource("masterRoutes")
    void contracts(String catalogueId, MasterType type, int operation) throws Exception {
        switch (operation) {
            case 0 -> {
                long count = switch (type) { case DEPARTMENT -> 5; case CATEGORY -> 6; case AREA -> 7; };
                var result = read(type.url()).andExpect(view().name("admin/" + type.plural)).andReturn();
                assertThat(page(result).totalItems()).isEqualTo(count);
                read(type.url() + "?active=false").andExpect(content().string(containsString("match the filter")));
            }
            case 1 -> read(type.url() + "/new").andExpect(view().name("admin/" + type.table + "-form"))
                    .andExpect(model().attribute("mode", "CREATE"));
            case 2 -> {
                save(type, null, "Lakeside", "6").andExpect(redirectedUrl(type.url()))
                        .andExpect(flash().attributeExists("flashSuccess"));
                assertThat(active(type, id(type, "Lakeside"))).isTrue();
                save(type, null, " ", "6").andExpect(model().attributeHasFieldErrors("form", "name"));
            }
            case 3 -> {
                long id = create(type, "Lakeside");
                read(type.url() + "/" + id + "/edit").andExpect(model().attribute("mode", "EDIT"))
                        .andExpect(content().string(containsString("Lakeside")));
                mvc.perform(get(type.url() + "/-1/edit").with(admin())).andExpect(status().isNotFound());
            }
            case 4 -> {
                long id = create(type, "Lakeside");
                save(type, id, "Lakeside", "90").andExpect(redirectedUrl(type.url()));
                save(type, id, "Green Park", "1").andExpect(redirectedUrl(type.url()));
                assertThat(id(type, "Green Park")).isEqualTo(id);
            }
            case 5 -> {
                long id = create(type, "Lakeside");
                toggle(type, id, false).andExpect(redirectedUrl(type.url()));
                toggle(type, id, true).andExpect(redirectedUrl(type.url())).andExpect(flash().attributeExists("flashSuccess"));
                assertThat(active(type, id)).isTrue();
            }
            case 6 -> {
                long id = create(type, "Lakeside");
                toggle(type, id, false).andExpect(redirectedUrl(type.url())).andExpect(flash().attributeExists("flashSuccess"));
                assertThat(active(type, id)).isFalse();
                assertThat(options(type)).extracting(LookupOption::id).doesNotContain(id);
            }
            default -> throw new AssertionError(catalogueId);
        }
    }

    @Test
    @DisplayName("TC-UC-40a a department can be created, edited, deactivated and activated")
    void manageDepartment() throws Exception {
        long parks = create(MasterType.DEPARTMENT, "Parks");
        mvc.perform(post("/admin/departments/{id}", parks).with(admin()).with(csrf())
                .param("name", "Parks").param("description", "Trees and open spaces"))
                .andExpect(flash().attribute("flashSuccess", "Department saved."));
        var listed = page(read("/admin/departments").andReturn());
        assertThat(listed.items()).anySatisfy(row -> {
            var department = (MasterDataRepository.Row) row;
            assertThat(department.name()).isEqualTo("Parks");
            assertThat(department.description()).isEqualTo("Trees and open spaces");
        });
        toggle(MasterType.DEPARTMENT, parks, false).andExpect(redirectedUrl("/admin/departments"));
        assertThat(active(MasterType.DEPARTMENT, parks)).isFalse();
        toggle(MasterType.DEPARTMENT, parks, true).andExpect(flash().attribute("flashSuccess", "Department activated."));
        assertThat(active(MasterType.DEPARTMENT, parks)).isTrue();
    }

    @Test
    @DisplayName("TC-UC-40b department names are compared without case or surrounding whitespace")
    void duplicateDepartment() throws Exception {
        save(MasterType.DEPARTMENT, null, " sanitation ", "5")
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "name"))
                .andExpect(content().string(containsString("This name is already in use.")));
        assertThat(page(read("/admin/departments").andReturn()).totalItems()).isEqualTo(5);
    }

    @Test
    @DisplayName("TC-UC-40c a department used by an active category cannot be deactivated")
    void defaultGuard() throws Exception {
        var electrical = id(MasterType.DEPARTMENT, "Electrical");
        toggle(MasterType.DEPARTMENT, electrical, false).andExpect(redirectedUrl("/admin/departments"))
                .andExpect(flash().attribute("flashError",
                        "Electrical is the default department of Streetlight. Change that category first."));
        assertThat(active(MasterType.DEPARTMENT, electrical)).isTrue();
    }

    @Test
    void categoryCannotReactivateWithInactiveDefault() throws Exception {
        long garbage = id(MasterType.CATEGORY, "Garbage");
        long sanitation = id(MasterType.DEPARTMENT, "Sanitation");
        toggle(MasterType.CATEGORY, garbage, false).andExpect(redirectedUrl("/admin/categories"));
        toggle(MasterType.DEPARTMENT, sanitation, false).andExpect(redirectedUrl("/admin/departments"));
        toggle(MasterType.CATEGORY, garbage, true).andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attributeExists("flashError"));
        assertThat(active(MasterType.CATEGORY, garbage)).isFalse();
        toggle(MasterType.DEPARTMENT, sanitation, true).andExpect(redirectedUrl("/admin/departments"));
        toggle(MasterType.CATEGORY, garbage, true).andExpect(flash().attributeExists("flashSuccess"));
        assertThat(active(MasterType.CATEGORY, garbage)).isTrue();
    }

    @Test
    @DisplayName("TC-UC-40d inactive departments are excluded from assignment options and retained in stored complaints")
    void departmentHistory() throws Exception {
        resetWithComplaintFixture();
        long parks = create(MasterType.DEPARTMENT, "Parks");
        jdbc.sql("UPDATE complaint SET department_id = :id WHERE complaint_no = :no")
                .param("id", parks).param("no", "GRV-2026-000005").update();
        toggle(MasterType.DEPARTMENT, parks, false).andExpect(redirectedUrl("/admin/departments"));
        assertThat(masterData.activeDepartments()).extracting(LookupOption::name).doesNotContain("Parks");
        assertThat(jdbc.sql("""
                SELECT d.name FROM complaint c JOIN department d ON d.id = c.department_id
                WHERE c.complaint_no = :no
                """).param("no", "GRV-2026-000005").query(String.class).single()).isEqualTo("Parks");
    }

    @Test
    @DisplayName("TC-UC-41a category creation and SLA updates keep the default department and active lookup")
    void manageCategory() throws Exception {
        long parks = create(MasterType.DEPARTMENT, "Parks");
        mvc.perform(post("/admin/categories").with(admin()).with(csrf())
                        .param("name", "Tree Fall").param("defaultDepartmentId", Long.toString(parks)).param("slaDays", "4"))
                .andExpect(redirectedUrl("/admin/categories"));
        long treeFall = id(MasterType.CATEGORY, "Tree Fall");
        mvc.perform(post("/admin/categories/{id}", treeFall).with(admin()).with(csrf())
                        .param("name", "Tree Fall").param("defaultDepartmentId", Long.toString(parks)).param("slaDays", "5"))
                .andExpect(redirectedUrl("/admin/categories"));
        assertThat(masterData.activeCategories()).extracting(LookupOption::id).contains(treeFall);
        assertThat(jdbc.sql("SELECT sla_days FROM category WHERE id = :id")
                .param("id", treeFall).query(Integer.class).single()).isEqualTo(5);
    }

    @ParameterizedTest(name = "TC-UC-41b SLA {0}")
    @CsvSource({"0", "91", "abc"})
    void invalidSla(String days) throws Exception {
        save(MasterType.CATEGORY, null, "Tree Fall", days).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "slaDays"));
    }

    @Test
    @DisplayName("TC-UC-41b a category must use an active default department")
    void invalidDefault() throws Exception {
        long parks = create(MasterType.DEPARTMENT, "Parks");
        toggle(MasterType.DEPARTMENT, parks, false);
        mvc.perform(post("/admin/categories").with(admin()).with(csrf()).param("name", "Tree Fall")
                        .param("slaDays", "4").param("defaultDepartmentId", Long.toString(parks)))
                .andExpect(model().attributeHasFieldErrorCode("form", "defaultDepartmentId",
                        "validation.defaultDepartmentId.inactive"));
    }

    @Test
    @DisplayName("TC-UC-41c inactive categories are hidden from the new complaint lookup while their history remains")
    void categoryHistory() throws Exception {
        resetWithComplaintFixture();
        long garbage = id(MasterType.CATEGORY, "Garbage");
        toggle(MasterType.CATEGORY, garbage, false).andExpect(redirectedUrl("/admin/categories"));
        assertThat(masterData.activeCategories()).extracting(LookupOption::id).doesNotContain(garbage);
        assertThat(jdbc.sql("SELECT category_id FROM complaint WHERE complaint_no = :no")
                .param("no", "GRV-2026-000003").query(Long.class).single()).isEqualTo(garbage);
    }

    @Test
    @DisplayName("TC-UC-42a renamed areas can be deactivated without losing their list entries")
    void manageArea() throws Exception {
        long id = create(MasterType.AREA, "Wakad");
        save(MasterType.AREA, id, "Wakad Ward", "4").andExpect(redirectedUrl("/admin/areas"));
        toggle(MasterType.AREA, id, false).andExpect(redirectedUrl("/admin/areas"));
        assertThat(masterData.activeAreas()).extracting(LookupOption::id).doesNotContain(id);
        read("/admin/areas?active=false").andExpect(content().string(containsString("Wakad Ward")));
    }

    @ParameterizedTest(name = "TC-VE-06 {0} {1} {2}")
    @CsvSource({"DEPARTMENT,name, ,name", "DEPARTMENT,name,A,name", "AREA,name,A,name", "CATEGORY,name,A,name",
            "CATEGORY,slaDays,,slaDays", "CATEGORY,slaDays,0,slaDays", "CATEGORY,slaDays,91,slaDays"})
    void validation(MasterType type, String param, String invalid, String field) throws Exception {
        var request = post(type.url()).with(admin()).with(csrf())
                .param(param, invalid == null ? "" : invalid);
        if (!param.equals("name")) request.param("name", "Valid location");
        if (type == MasterType.CATEGORY && !param.equals("slaDays")) request.param("slaDays", "10");
        mvc.perform(request).andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", field));
    }

    @Test
    @DisplayName("TC-VE-06 description length and both inclusive SLA endpoints are enforced")
    void bounds() throws Exception {
        for (var type : new MasterType[]{MasterType.DEPARTMENT, MasterType.AREA}) {
            mvc.perform(post(type.url()).with(admin()).with(csrf()).param("name", "Lakeside")
                            .param("description", "a".repeat(256)))
                    .andExpect(model().attributeHasFieldErrors("form", "description"));
        }
        save(MasterType.CATEGORY, null, "Near limit", "1").andExpect(status().isFound());
        save(MasterType.CATEGORY, null, "Far limit", "90").andExpect(status().isFound());
    }

    @Test
    @DisplayName("TC-VE-11 category SLA binding produces the route-specific conversion message")
    void categorySlaBinding() throws Exception {
        var response = save(MasterType.CATEGORY, null, "Tree Fall", "abc")
                .andExpect(view().name("admin/category-form"))
                .andExpect(content().string(containsString("SLA days must be a whole number from 1 to 90.")))
                .andReturn();
        var model = response.getModelAndView().getModel();
        var errors = (BindingResult) model.get(BindingResult.MODEL_KEY_PREFIX + "form");
        assertThat(errors.getFieldError("slaDays").getCodes()).contains("typeMismatch.slaDays");
    }

    @Test
    void adminAuditUsesActorAndTargetIdentifiers() throws Exception {
        long adminId = details.loadUserByUsername(properties.admin().email()).id();
        long departmentId = create(MasterType.DEPARTMENT, "Parks");
        toggle(MasterType.DEPARTMENT, departmentId, false).andExpect(redirectedUrl("/admin/departments"));
        assertThat(logs.entries().stream().map(event -> event.getFormattedMessage())
                .filter(message -> message.startsWith("ADMIN_ACTION")).toList())
                .containsExactly("ADMIN_ACTION adminId=" + adminId + " action=masterdata_create targetType=department targetId=" + departmentId,
                        "ADMIN_ACTION adminId=" + adminId + " action=masterdata_deactivate targetType=department targetId=" + departmentId);
    }

    private void resetWithComplaintFixture() throws Exception {
        TestDatabase.reset(dataSource);
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
    }

    private org.springframework.test.web.servlet.ResultActions read(String url) throws Exception {
        return mvc.perform(get(url).with(admin())).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions save(MasterType type, Long id, String name, String days)
            throws Exception {
        var request = post(type.url() + (id == null ? "" : "/" + id)).with(admin()).with(csrf()).param("name", name);
        if (type == MasterType.CATEGORY) request.param("slaDays", days);
        return mvc.perform(request);
    }

    private long create(MasterType type, String name) throws Exception {
        save(type, null, name, "4").andExpect(redirectedUrl(type.url()));
        return id(type, name);
    }

    private org.springframework.test.web.servlet.ResultActions toggle(MasterType type, long id, boolean active) throws Exception {
        return mvc.perform(post(type.url() + "/" + id + (active ? "/activate" : "/deactivate")).with(admin()).with(csrf()));
    }

    private boolean active(MasterType type, long id) {
        return jdbc.sql("SELECT active FROM " + type.table + " WHERE id = :id")
                .param("id", id).query(Boolean.class).single();
    }

    private long id(MasterType type, String name) {
        return jdbc.sql("SELECT id FROM " + type.table + " WHERE name = :name")
                .param("name", name).query(Long.class).single();
    }

    private java.util.List<LookupOption> options(MasterType type) {
        return switch (type) {
            case DEPARTMENT -> masterData.activeDepartments();
            case CATEGORY -> masterData.activeCategories();
            case AREA -> masterData.activeAreas();
        };
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return Logins.appUser(details, properties.admin().email());
    }

    private static PageResult<?> page(MvcResult result) {
        return (PageResult<?>) result.getModelAndView().getModel().get("page");
    }
}
