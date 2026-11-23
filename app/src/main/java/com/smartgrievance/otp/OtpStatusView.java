package com.smartgrievance.otp;

import java.time.LocalDateTime;

public record OtpStatusView(String maskedMobile, LocalDateTime sentAt, LocalDateTime expiresAt,
                            int attemptsLeft, LocalDateTime resendAllowedAt, boolean resendAllowed,
                            boolean hourlyLimitReached, String demoCode) {}
