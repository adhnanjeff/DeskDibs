package com.deskdibs.common;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Replaces the system clock with one the test drives, so time-dependent rules — the booking
 * horizon, the 10:00 team-block release, "is this booking for today" — can be exercised at the
 * exact moment that matters instead of whenever the suite happens to run.
 *
 * <p>{@code @Primary} rather than a bean-name override, so {@code OfficeClock} picks this up while
 * the production {@code systemClock} bean stays defined and untouched.
 *
 * <p>Imported explicitly by the tests that need it; the fixed starting moment is a Monday morning
 * in the office, before every cut-off the office has.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ControllableClockConfiguration {

    /** A Monday. */
    public static final LocalDate DEFAULT_TODAY = LocalDate.of(2026, 8, 10);

    /** Before the 10:00 team-block release and before the 11:00 no-show release. */
    public static final LocalTime DEFAULT_TIME_OF_DAY = LocalTime.of(9, 0);

    @Bean
    @Primary
    public MutableClock testClock(OfficeProperties office) {
        ZonedDateTime start = ZonedDateTime.of(DEFAULT_TODAY, DEFAULT_TIME_OF_DAY, office.timezone());
        return new MutableClock(start.toInstant(), office.timezone());
    }
}
