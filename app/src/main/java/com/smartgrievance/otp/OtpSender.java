package com.smartgrievance.otp;

public interface OtpSender {
    String send(long userId, String mobile, String code);
}
