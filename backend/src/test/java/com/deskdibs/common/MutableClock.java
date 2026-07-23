package com.deskdibs.common;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock a test can move.
 *
 * <p>The 10:00 team-block release is a rule about time, and the only honest way to test a rule
 * about time is to control it. Sleeping until 10:00 is not a test.
 *
 * <p>The instant lives in a shared {@link AtomicReference} so that a zone-shifted copy handed out
 * by {@link #withZone(ZoneId)} keeps seeing later moves, and so a move made by the test thread is
 * visible to the 150 threads of the concurrency test.
 */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this(new AtomicReference<>(instant), zone);
    }

    private MutableClock(AtomicReference<Instant> instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /** Move the office to this moment. */
    public void setTo(ZonedDateTime moment) {
        instant.set(moment.toInstant());
    }

    @Override
    public Instant instant() {
        return instant.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId other) {
        return other.equals(zone) ? this : new MutableClock(instant, other);
    }
}
