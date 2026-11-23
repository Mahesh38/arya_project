package com.smartgrievance.testsupport;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.smartgrievance.config.AppProperties;

@Tag("flow")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@ActiveProfiles("test")
public abstract class FlowTestBase {
    @Autowired protected DataSource dataSource;
    @Autowired protected TestClock testClock;
    @Autowired protected AppProperties properties;

    @BeforeEach
    void resetFoundation() throws Exception {
        testClock.reset();
        TestDatabase.reset(dataSource);
        TestDatabase.emptyUploads(properties.upload().dir());
    }
}
