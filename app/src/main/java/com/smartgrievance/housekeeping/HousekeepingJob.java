package com.smartgrievance.housekeeping;

import com.smartgrievance.config.AppProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HousekeepingJob {
    private static final Logger LOG = LoggerFactory.getLogger(HousekeepingJob.class);
    private final HousekeepingRepository repository;
    private final AppProperties.Housekeeping config;
    private final Clock clock;

    HousekeepingJob(HousekeepingRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.config = properties.housekeeping();
        this.clock = clock;
    }

    @Scheduled(cron = "${app.housekeeping.cron}", zone = "${app.timezone}")
    public void run() {
        var now = LocalDateTime.now(clock);
        int size = config.batchSize();
        int notifications = purge(() -> repository.deleteNotifications(now.minusDays(config.notificationRetentionDays()), size));
        int otps = purge(() -> repository.deleteOtps(now.minusDays(config.otpRetentionDays()), size));
        LOG.info("HOUSEKEEPING notificationsDeleted={} otpsDeleted={}", notifications, otps);
    }

    private int purge(IntSupplier deleteBatch) {
        int total = 0;
        int deleted;
        do {
            deleted = deleteBatch.getAsInt();
            total += deleted;
        } while (deleted == config.batchSize());
        return total;
    }
}
