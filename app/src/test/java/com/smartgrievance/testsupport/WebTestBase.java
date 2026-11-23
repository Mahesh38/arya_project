package com.smartgrievance.testsupport;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@Tag("web")
public abstract class WebTestBase extends FlowTestBase {
    @Autowired protected MockMvc mvc;
}
