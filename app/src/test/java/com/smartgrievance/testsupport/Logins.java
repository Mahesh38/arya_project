package com.smartgrievance.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartgrievance.user.AppUserDetailsService;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class Logins {
    private Logins() {}

    public static MockHttpSession formLogin(MockMvc mvc, String email, String password) throws Exception {
        var result = mvc.perform(post("/login").with(csrf()).param("email", email).param("password", password))
                .andExpect(status().isFound()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    public static RequestPostProcessor appUser(AppUserDetailsService users, String email) {
        var principal = users.loadUserByUsername(email);
        principal.eraseCredentials();
        return user(principal);
    }
}
