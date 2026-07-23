package com.deskdibs.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.LocalTime;
import java.time.ZoneId;

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
 * @param noShowReleaseTime    when an un-checked-in booking goes back into the pool. Read by the
 *                             scheduled job in a later phase; declared here so the whole block
 *                             binds and validates as one.
 */
@ConfigurationProperties(prefix = "deskdibs.office")
@Validated
public record OfficeProperties(

        @NotNull ZoneId timezone,

        @Min(0) @Max(365) int bookingHorizonDays,

        @NotNull LocalTime teamBlockReleaseTime,

        @NotNull LocalTime noShowReleaseTime) {
}
