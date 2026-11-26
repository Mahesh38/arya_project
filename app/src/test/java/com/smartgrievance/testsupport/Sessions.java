package com.smartgrievance.testsupport;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class Sessions {
    private Sessions() {}

    public static RequestPostProcessor requestedSession(MockHttpSession session) {
        return request -> {
            request.setSession(session);
            request.setRequestedSessionId(session.getId());
            request.setRequestedSessionIdValid(true);
            request.setRequestedSessionIdFromCookie(true);
            return request;
        };
    }

    public static RequestPostProcessor invalidSession() {
        return request -> {
            request.setRequestedSessionId("expired-session");
            request.setRequestedSessionIdValid(false);
            request.setRequestedSessionIdFromCookie(true);
            return request;
        };
    }
}
