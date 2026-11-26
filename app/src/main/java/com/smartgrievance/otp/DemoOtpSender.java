package com.smartgrievance.otp;

import com.smartgrievance.security.SecurityEvents;
import org.springframework.stereotype.Component;

@Component
public class DemoOtpSender implements OtpSender {
    @Override
    public String send(long userId, String mobile, String code) {
        SecurityEvents.LOG.info("DEMO-OTP userId={} mobile={} code={}", userId, mask(mobile), code);
        return code;
    }

    static String mask(String mobile) {
        return "******" + mobile.substring(mobile.length() - 4);
    }
}
