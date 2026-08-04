package com.deskdibs.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * The office's rules about time, bound from the {@code deskdibs.office} block of
 * {@code application.yml}. Cut-offs and the booking horizon are configuration, never constants
 * buried in a service.
 *
 * <p>{@code @Validated} makes a wrong value a startup failure rather than a booking that silently
 * behaves oddly at 10:00 six months from now: an unparseable timezone, a negative horizon, or a
 * missing release time stops the application before it can accept a single claim.
 *
 * @param timezone             the zone every date and cut-off resolves in. The client clock is
 *                             never trusted, so this is the only definition of "today".
 * @param bookingHorizonDays   how far ahead a seat may be claimed. {@code 0} would mean today only.
 * @param teamBlockReleaseTime default release time for a new team hold; an individual
 *                             {@code seat_reservation} row carries its own, which wins.
 * @param noShowReleaseTime    when an un-checked-in booking goes back into the pool, read by
 *                             {@code NoShowReleaseScheduler}.
 * @param checkInOpensTime     the earliest hour a booking may be checked in. Without it check-in
 *                             opens at midnight, which makes it worthless as evidence of turning
 *                             up: nothing stops somebody claiming their desk at 00:05 from home and
 *                             surviving the 11:00 release without ever entering the building.
 * @param sameDayCutoffTime    the time of day after which today stops being offered as a bookable
 *                             day. Past it the working day is underway and the no-show release has
 *                             already recycled the desks nobody turned up for, so a strip still
 *                             leading with "Today" is offering a day that has largely gone.
 * @param workingDays          the days the office is open, so a desk cannot be booked for a day
 *                             nobody is there. Enforced by the booking rules, not only shown in
 *                             the date strip.
 * @param noShowReleaseDays    which days that release runs on, as a cron day-of-week field
 *                             ({@code MON-FRI}). PLAN.md §12 still lists the office's working days
 *                             as unconfirmed, so this stays configuration rather than a hardcoded
 *                             Monday-to-Friday assumption baked into a job.
 */
@ConfigurationProperties(prefix = "deskdibs.office")
@Validated
public record OfficeProperties(

        @NotNull ZoneId timezone,

        @Min(0) @Max(365) int bookingHorizonDays,

        @NotNull LocalTime teamBlockReleaseTime,

        @NotNull LocalTime noShowReleaseTime,

        @NotNull LocalTime checkInOpensTime,

        @NotNull LocalTime sameDayCutoffTime,

        @NotBlank String noShowReleaseDays,

        @NotEmpty Set<DayOfWeek> workingDays) {

    /**
     * Is the office open on {@code date}?
     *
     * <p>Asked by the booking rules, not only by the UI: a weekend the interface greys out is a
     * weekend a scripted request can still book. The set is configuration because PLAN.md §12 lists
     * the office's working days as unconfirmed, and offices in some regions do not run Mon–Fri.
     */
    public boolean isWorkingDay(LocalDate date) {
        return workingDays.contains(date.getDayOfWeek());
    }
}
