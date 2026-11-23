package com.smartgrievance.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.common.validation.UniqueKeys;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.FlowTestBase;
import com.smartgrievance.user.AccountBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** D84: the MySQL driver names the key as table.constraint; UniqueKeys must map it to the form field (H2: TC-VE-16). */
@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
class UniqueKeysMySqlIT extends FlowTestBase {
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void seed() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
    }

    @ParameterizedTest(name = "TC-Q-43 {0}.{1} reported as {2} maps to field {3}")
    @CsvSource({
            "app_user,email,app_user.uq_app_user_email,email,'jayesh@example.com','mahesh@example.com'",
            "app_user,mobile,app_user.uq_app_user_mobile,mobile,'jayesh@example.com','9800000001'",
            "department,name,department.uq_department_name,name,'Water Supply','Sanitation'",
            "category,name,category.uq_category_name,name,'Garbage','Drainage'",
            "area,name,area.uq_area_name,name,'Baner','Kothrud'"})
    void duplicateKeysMapToFields(String table, String column, String reported, String field, String row, String taken) {
        String key = table.equals("app_user") ? "email" : "name";
        // table and column names come from the fixed CSV rows above, never from input
        var duplicate = catchThrowableOfType(DuplicateKeyException.class, () -> jdbc.sql(
                "UPDATE " + table + " SET " + column + " = :taken WHERE " + key + " = :row")
                .param("taken", taken).param("row", row).update());
        assertThat(duplicate).isNotNull();
        assertThat(duplicate.getMostSpecificCause().getMessage()).contains("Duplicate entry").contains(reported);
        var mapped = UniqueKeys.translate(duplicate);
        assertThat(mapped).isInstanceOf(FormRejectedException.class);
        assertThat(((FormRejectedException) mapped).errors().getFirst().field()).isEqualTo(field);
    }
}
