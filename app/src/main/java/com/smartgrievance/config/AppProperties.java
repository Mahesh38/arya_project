package com.smartgrievance.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/** Every app.* key of doc 14 §5.5. Unknown keys under "app" are refused at start-up. */
@Validated
@ConfigurationProperties(prefix = "app", ignoreUnknownFields = false)
public record AppProperties(
        @NotNull ZoneId timezone,
        @Valid @NotNull Complaint complaint,
        @Valid @NotNull Housekeeping housekeeping,
        @Valid @NotNull Upload upload,
        @Valid @NotNull Export export,
        @Valid @NotNull Otp otp,
        @Valid @NotNull Security security,
        @Valid @NotNull Admin admin) {

    public record Complaint(@Min(1) int reviewWindowDays, @Min(0) int maxReopens,
                            @NotBlank String autoCloseCron, @Min(1) int maxPerDay) {}

    public record Housekeeping(@NotBlank String cron, @Min(1) int notificationRetentionDays,
                               @Min(1) int otpRetentionDays, @Min(1) int batchSize) {}

    public record Upload(@NotNull Path dir, @NotNull DataSize maxFileSize,
                         @Min(1) @Max(5) int maxFilesPerComplaint, @NotEmpty List<String> allowedTypes) {}

    public record Export(@Min(1) int maxRows) {}

    public record Otp(@Pattern(regexp = "demo") String sender, @Min(4) @Max(8) int length,
                      @NotNull Duration ttl, @Min(1) int maxAttempts,
                      @NotNull Duration resendCooldown, @Min(1) int maxSendsPerHour) {}

    public record Security(@Min(4) @Max(31) int bcryptStrength, @Valid @NotNull Login login,
                           @Valid @NotNull Register register) {
        public record Login(@Min(1) int maxFailuresPerAccount, @Min(1) int maxFailuresPerIp,
                            @NotNull Duration window, @NotNull Duration lock) {}
        public record Register(@Min(1) int maxPerIpPerHour) {}
    }

    public record Admin(@NotBlank @Email String email, String password,
                        @NotBlank String name, @Pattern(regexp = "[6-9]\\d{9}") String mobile) {}
}
