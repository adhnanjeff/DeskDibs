package com.deskdibs.booking;

import com.deskdibs.common.OfficeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.time.LocalTime;

/**
 * Fires {@link NoShowReleaseService} at the office's no-show cut-off.
 *
 * <p>The schedule is built from {@code deskdibs.office} at startup rather than written into a
 * {@code @Scheduled(cron = "...")} literal, because the cut-off time, the working days and the
 * timezone are all configuration — the same values the booking rules resolve against. A hardcoded
 * cron would drift from them the first time the office changed its hours.
 *
 * <p>The trigger runs in the office timezone, so the job fires at 11:00 <em>there</em> regardless of
 * where the server is or whether it is in daylight saving.
 *
 * <p>Disabled across the test suite via {@code deskdibs.scheduling.enabled}: tests call the service
 * directly against a movable clock, which proves the behaviour without a test that waits for a
 * trigger — and stops a background release from mutating rows another test is asserting on.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "deskdibs.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class NoShowReleaseScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(NoShowReleaseScheduler.class);

    private final NoShowReleaseService releaseService;
    private final OfficeProperties office;

    public NoShowReleaseScheduler(NoShowReleaseService releaseService, OfficeProperties office) {
        this.releaseService = releaseService;
        this.office = office;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        LocalTime at = office.noShowReleaseTime();
        // Spring's six-field cron: second minute hour day-of-month month day-of-week.
        String cron = "0 %d %d * * %s".formatted(at.getMinute(), at.getHour(), office.noShowReleaseDays());

        log.info("No-show release scheduled at {} {} ({}) in {}",
                at, office.noShowReleaseDays(), cron, office.timezone());

        registrar.addCronTask(new CronTask(this::runRelease, new CronTrigger(cron, office.timezone())));
    }

    /**
     * Never lets an exception escape into the scheduler. An uncaught failure would suppress
     * subsequent executions of this trigger, so one bad morning would silently stop every
     * following day's release — the failure mode nobody notices until seats stop freeing up.
     */
    private void runRelease() {
        try {
            releaseService.releaseTodaysNoShows();
        } catch (RuntimeException e) {
            log.error("No-show release failed; seats stay held until the next run", e);
        }
    }
}
