package com.deskdibs.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The one place the system is allowed to ask what time it is.
 *
 * <p>Two rules meet here. The client clock is never trusted, so "today" and every cut-off resolve
 * server-side in the configured office timezone. And the server clock is never read directly
 * either — {@code LocalDate.now()} scattered through services cannot be moved, which would make
 * the 10:00 team-block release testable only by waiting for 10:00.
 *
 * <p>Everything is derived from {@link Clock#instant()} on each call rather than from a
 * zone-shifted copy taken at construction, so a test clock that is moved after startup is seen
 * immediately.
 */
@Component
public class OfficeClock {

    private final Clock clock;
    private final ZoneId zone;

    public OfficeClock(Clock clock, OfficeProperties office) {
        this.clock = clock;
        this.zone = office.timezone();
    }

    /** The office timezone every date and cut-off in the system is expressed in. */
    public ZoneId zone() {
        return zone;
    }

    public Instant instant() {
        return clock.instant();
    }

    public ZonedDateTime now() {
        return clock.instant().atZone(zone);
    }

    /** Today in the office, which is the only "today" the system recognises. */
    public LocalDate today() {
        return now().toLocalDate();
    }

    /** Wall-clock time in the office. */
    public LocalTime timeOfDay() {
        return now().toLocalTime();
    }

    /** Now, shaped for a {@code timestamptz} column. */
    public OffsetDateTime timestamp() {
        return now().toOffsetDateTime();
    }

    /**
     * Is the office clock still before {@code time} on {@code date}?
     *
     * <p>Deliberately a full date-and-time comparison rather than a time-of-day one. A team hold on
     * next Tuesday's seat must not evaporate the moment today passes 10:00 — it releases at 10:00
     * <em>on Tuesday</em>. For a hold on today this reduces to the intended "is it before 10:00 yet".
     */
    public boolean isBefore(LocalDate date, LocalTime time) {
        return now().isBefore(date.atTime(time).atZone(zone));
    }
}
