package com.smartgrievance.workflow;

import com.smartgrievance.complaint.ComplaintRepository;
import com.smartgrievance.complaint.ComplaintRepository.Expired;
import com.smartgrievance.config.AppProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutoCloseJob {
    private static final Logger LOG = LoggerFactory.getLogger(AutoCloseJob.class);
    private static final int BATCH_SIZE = 500;
    private final ComplaintRepository complaints;
    private final ComplaintWorkflowService workflow;
    private final Clock clock;
    private final int reviewWindowDays;

    AutoCloseJob(ComplaintRepository complaints, ComplaintWorkflowService workflow, Clock clock, AppProperties properties) {
        this.complaints = complaints;
        this.workflow = workflow;
        this.clock = clock;
        this.reviewWindowDays = properties.complaint().reviewWindowDays();
    }

    @Scheduled(cron = "${app.complaint.auto-close-cron}", zone = "${app.timezone}")
    public void run() {
        var time = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        var cutoff = time.minusDays(reviewWindowDays);
        Expired after = null;
        int closed = 0;
        List<Expired> batch;
        do {
            batch = complaints.findExpiredResolved(cutoff, BATCH_SIZE, after);
            for (Expired item : batch) {
                try {
                    if (workflow.autoClose(item, time)) closed++;
                } catch (RuntimeException failure) {
                    LOG.error("AUTO_CLOSE_FAILED complaintNo={}", item.number(), failure);
                }
                after = item;
            }
        } while (batch.size() == BATCH_SIZE);
        LOG.info("Auto-close job closed {} complaints", closed);
    }
}
