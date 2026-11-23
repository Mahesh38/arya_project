package com.smartgrievance.masterdata;

import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.testsupport.LogCapture;
import com.smartgrievance.testsupport.RepositoryTestBase;
import com.smartgrievance.testsupport.TestDatabase;
import com.smartgrievance.user.AccountService;
import com.smartgrievance.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminUniqueRaceTest extends RepositoryTestBase {
    @RegisterExtension final LogCapture logs = new LogCapture();

    @ParameterizedTest(name = "TC-VE-16 {0} name constraint becomes a form error")
    @CsvSource({"DEPARTMENT,Sanitation,uq_department_name", "CATEGORY,Garbage,uq_category_name", "AREA,Baner,uq_area_name"})
    void duplicateNames(MasterType type, String name, String constraint) {
        // Model the gap between an earlier availability check and a concurrent insertion.
        var data = new MasterDataRepository(jdbc) {
            @Override
            boolean nameTaken(MasterType type, String name, Long id) { return false; }
        };
        var service = new MasterDataService(data, testClock);
        var form = service.form(type, null);
        form.setName(name);
        if (form instanceof CategoryForm category) category.setSlaDays(4);
        assertRejection(() -> service.save(type, null, form, 1L), "name", constraint);
    }

    @ParameterizedTest(name = "TC-VE-16 officer {0} constraint becomes a form error")
    @CsvSource({"email,uq_app_user_email", "mobile,uq_app_user_mobile"})
    void duplicateIdentity(String field, String constraint) {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        var data = new UserRepository(jdbc) {
            @Override
            public Optional<com.smartgrievance.user.Account> byEmail(String email) { return Optional.empty(); }

            @Override
            public boolean mobileTaken(String mobile) { return false; }
        };
        var service = new AccountService(data, new BCryptPasswordEncoder(4), testClock);
        String email = field.equals("email") ? "admin@example.com" : "race@example.test";
        String mobile = field.equals("mobile") ? "9000000000" : "7800112233";
        assertRejection(() -> service.createOfficer("Neha Testing", email, mobile, "Nacre!Cascade77", 1L),
                field, constraint);
    }

    private void assertRejection(Runnable save, String field, String constraint) {
        assertThatThrownBy(save::run).isInstanceOfSatisfying(FormRejectedException.class, rejected ->
                assertThat(rejected.errors()).singleElement().satisfies(error -> {
                    assertThat(error.field()).isEqualTo(field);
                    assertThat(error.key()).isEqualTo("validation." + field + ".taken");
                }));
        assertThat(logs.entries()).extracting(event -> event.getFormattedMessage())
                .filteredOn(message -> message.contains("UNIQUE_RACE"))
                .containsExactly("UNIQUE_RACE constraint=" + constraint);
    }
}
