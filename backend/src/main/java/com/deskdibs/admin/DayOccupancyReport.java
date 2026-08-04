package com.deskdibs.admin;

import com.deskdibs.booking.BookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Who sat where on one day — the admin's after-the-fact record of a date.
 *
 * <p>Deliberately not the seat map for that date. The map answers "what can I book", so it only
 * carries live bookings; this answers "what happened", so it keeps the rows the map drops:
 * a desk somebody booked and then cancelled, and one the no-show release took back, are both part
 * of the day's story and neither appears on a map.
 *
 * @param date         the day reported on, echoed back so an exported file is self-describing
 * @param totalSeats   every seat on the floor, including any out of service
 * @param bookedSeats  desks with a live booking at the end of the day
 * @param attended     of those, the ones whose holder checked in
 * @param noShows      booked, never checked in, and taken back by the release
 * @param cancelled    given up by the person who held them
 * @param rows         one per booking, in seat order
 */
public record DayOccupancyReport(
        LocalDate date,
        int totalSeats,
        int bookedSeats,
        int attended,
        int noShows,
        int cancelled,
        List<Row> rows) {

    /**
     * One desk's day.
     *
     * @param checkedInAt when they confirmed they were in, or {@code null} if they never did
     * @param team        the teams the occupant belongs to, joined for readability — somebody may
     *                    sit with more than one group, which is why this is a list and not a field
     */
    public record Row(
            @Schema(example = "R3-A2") String seatLabel,
            long userId,
            @Schema(example = "Alice M.") String userName,
            @Schema(example = "alice@deskdibs.test") String userEmail,
            List<String> team,
            BookingStatus status,
            @Schema(nullable = true) OffsetDateTime checkedInAt) {

        /** Whether this desk was actually sat at, as far as the system can tell. */
        public boolean attended() {
            return checkedInAt != null;
        }
    }
}
