package com.smartgrievance.testsupport;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.smartgrievance.config.AppProperties;

@Tag("http")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClockConfig.class)
@ActiveProfiles("test")
public abstract class HttpTestBase {
    @LocalServerPort protected int port;
    @Autowired protected TestClock testClock;
    @Autowired protected DataSource dataSource;
    @Autowired protected AppProperties properties;
    protected HttpTestClient http;

    @BeforeEach
    void resetFoundation() throws Exception {
        testClock.reset();
        TestDatabase.reset(dataSource);
        TestDatabase.emptyUploads(properties.upload().dir());
        http = new HttpTestClient(port);
    }
}
