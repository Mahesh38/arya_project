package com.smartgrievance.masterdata;

import java.time.LocalDateTime;

import com.smartgrievance.config.AppProperties;
import com.smartgrievance.testsupport.Logins;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@Tag("mysql")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "mysqltest"})
class MasterDataMySqlIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired AppProperties properties;
    @Autowired AccountBootstrap bootstrap;
    @Autowired AppUserDetailsService details;
    @Autowired PasswordEncoder passwords;

    @Test
    @DisplayName("TC-BR-35 MySQL stores a verified officer and rejects an unverified staff state")
    void persistsMasterDataAndAccountChanges() throws Exception {
        bootstrap.ensureAdmin(properties.admin(), false);
        var admin = Logins.appUser(details, properties.admin().email());
        String run = Long.toString(System.currentTimeMillis());
        String departmentName = "MySQL Ward " + run;
        String categoryName = "MySQL Category " + run;
        String email = "officer-mysql-" + run + "@example.test";
        String password = "Nacre!Cascade77";

        try {
            mvc.perform(post("/admin/departments").with(admin).with(csrf())
                            .param("name", departmentName).param("description", "Ward service desk"))
                    .andExpect(redirectedUrl("/admin/departments"));
            long departmentId = jdbc.sql("SELECT id FROM department WHERE name = :name")
                    .param("name", departmentName).query(Long.class).single();
            mvc.perform(post("/admin/categories").with(admin).with(csrf())
                            .param("name", categoryName).param("slaDays", "7")
                            .param("defaultDepartmentId", Long.toString(departmentId)))
                    .andExpect(redirectedUrl("/admin/categories"));
            mvc.perform(post("/admin/departments/{id}/deactivate", departmentId).with(admin).with(csrf()))
                    .andExpect(redirectedUrl("/admin/departments")).andExpect(flash().attributeExists("flashError"));
            assertThat(jdbc.sql("SELECT active FROM department WHERE id = :id").param("id", departmentId)
                    .query(Boolean.class).single()).isTrue();
            assertThat(jdbc.sql("SELECT default_department_id FROM category WHERE name = :name")
                    .param("name", categoryName).query(Long.class).single()).isEqualTo(departmentId);

            mvc.perform(post("/admin/officers").with(admin).with(csrf()).param("fullName", "Asha Testing")
                            .param("email", email).param("mobile", "75" + run.substring(run.length() - 8))
                            .param("departmentId", Long.toString(departmentId)).param("password", password)
                            .param("confirmPassword", password))
                    .andExpect(redirectedUrl("/admin/officers"));
            var officer = officer(email);
            assertThat(officer.role()).isEqualTo("OFFICER");
            assertThat(officer.departmentId()).isEqualTo(departmentId);
            assertThat(officer.active()).isTrue();
            assertThat(officer.verified()).isTrue();
            assertThat(passwords.matches(password, officer.hash())).isTrue();
            assertThatThrownBy(() -> jdbc.sql("UPDATE app_user SET mobile_verified = FALSE WHERE id = :id")
                    .param("id", officer.id()).update()).isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("ck_app_user_staff_verified");

            mvc.perform(post("/admin/officers/{id}/deactivate", officer.id()).with(admin).with(csrf())
                            .param("version", Integer.toString(officer.version())))
                    .andExpect(redirectedUrl("/admin/officers")).andExpect(flash().attributeExists("flashSuccess"));
            var deactivated = officer(email);
            assertThat(deactivated.active()).isFalse();
            assertThat(deactivated.version()).isEqualTo(officer.version() + 1);
            mvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                    .andExpect(redirectedUrl("/login?error"));
        } finally {
            jdbc.sql("DELETE FROM app_user WHERE email = :email").param("email", email).update();
            jdbc.sql("DELETE FROM category WHERE name = :name").param("name", categoryName).update();
            jdbc.sql("DELETE FROM department WHERE name = :name").param("name", departmentName).update();
        }
    }

    @Test
    @DisplayName("TC-BR-29 MySQL and the officer form enforce the department boundary for each account role")
    void enforcesRoleDepartmentBoundary() throws Exception {
        bootstrap.ensureAdmin(properties.admin(), false);
        var admin = Logins.appUser(details, properties.admin().email());
        String run = Long.toString(System.currentTimeMillis());
        String email = "missing-department-" + run + "@example.test";
        String mobile = "74" + run.substring(run.length() - 8);
        String password = "Nacre!Cascade77";
        try {
            mvc.perform(post("/admin/officers").with(admin).with(csrf()).param("fullName", "Asha Testing")
                            .param("email", email).param("mobile", mobile).param("password", password)
                            .param("confirmPassword", password))
                    .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "departmentId"));
            long departmentId = jdbc.sql("SELECT id FROM department WHERE name = 'Water Supply'").query(Long.class).single();
            var now = LocalDateTime.now(properties.timezone());
            assertThatThrownBy(() -> jdbc.sql("""
                    INSERT INTO app_user (full_name, email, mobile, password_hash, role, department_id,
                                          active, mobile_verified, created_at, updated_at)
                    VALUES ('Asha Testing', :email, :mobile, :hash, 'CITIZEN', :department, TRUE, TRUE, :now, :now)
                    """).param("email", email).param("mobile", mobile).param("hash", passwords.encode(password))
                    .param("department", departmentId).param("now", now).update())
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_app_user_officer_dept");
        } finally {
            jdbc.sql("DELETE FROM app_user WHERE email = :email").param("email", email).update();
        }
    }

    private Officer officer(String email) {
        return jdbc.sql("""
                SELECT id, role, department_id, active, mobile_verified, password_hash, version
                FROM app_user WHERE email = :email
                """).param("email", email).query((row, index) -> new Officer(row.getLong("id"),
                        row.getString("role"), row.getLong("department_id"), row.getBoolean("active"),
                        row.getBoolean("mobile_verified"), row.getString("password_hash"), row.getInt("version"))).single();
    }

    private record Officer(long id, String role, long departmentId, boolean active, boolean verified,
                           String hash, int version) {}
}
