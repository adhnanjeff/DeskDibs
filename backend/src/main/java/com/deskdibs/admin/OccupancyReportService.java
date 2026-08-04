package com.deskdibs.admin;

import com.deskdibs.booking.Booking;
import com.deskdibs.booking.BookingRepository;
import com.deskdibs.booking.BookingStatus;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.team.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the admin's record of one past (or present) day: who sat where.
 *
 * <h2>Three queries, whatever the floor holds</h2>
 * The bookings for the date with seat and user joined, the seat count, and — only if anybody was
 * booked — one grouped lookup of team names for exactly those occupants. Reading
 * {@code booking.getUser().getTeams()} per row instead would be a lazy load per person, which is
 * the shape of N+1 that only shows itself once the office is full.
 */
@Service
public class OccupancyReportService {

    private final BookingRepository bookings;
    private final SeatRepository seats;
    private final TeamMemberRepository teamMembers;

    public OccupancyReportService(BookingRepository bookings,
                                  SeatRepository seats,
                                  TeamMemberRepository teamMembers) {
        this.bookings = bookings;
        this.seats = seats;
        this.teamMembers = teamMembers;
    }

    /**
     * Every booking made for {@code date}, whatever became of it.
     *
     * <p>Cancelled and no-show rows are kept rather than filtered. A report that showed only live
     * bookings would answer "who has a desk" — which the seat map already answers, for today only —
     * instead of "what happened on the 4th", and it is the second question somebody opens a report
     * to ask. The counts break the rows down so the summary is still readable at a glance.
     */
    @Transactional(readOnly = true)
    public DayOccupancyReport forDate(LocalDate date) {
        List<Booking> onDate = bookings.findAllOnDateFetchSeatAndUser(date);

        Set<Long> occupantIds = onDate.stream().map(b -> b.getUser().getId()).collect(Collectors.toSet());
        Map<Long, List<String>> teamsByUser = occupantIds.isEmpty()
                ? Map.of()
                : teamMembers.findTeamNamesForUsers(occupantIds).stream()
                        .collect(Collectors.groupingBy(
                                row -> (Long) row[0],
                                Collectors.mapping(row -> (String) row[1], Collectors.toList())));

        List<DayOccupancyReport.Row> rows = new ArrayList<>(onDate.size());
        int booked = 0;
        int attended = 0;
        int noShows = 0;
        int cancelled = 0;

        for (Booking booking : onDate) {
            BookingStatus status = booking.getStatus();
            switch (status) {
                case ACTIVE -> {
                    booked++;
                    if (booking.getCheckedInAt() != null) {
                        attended++;
                    }
                }
                case RELEASED_NO_SHOW -> noShows++;
                case CANCELLED -> cancelled++;
            }

            rows.add(new DayOccupancyReport.Row(
                    booking.getSeat().getLabel(),
                    booking.getUser().getId(),
                    booking.getUser().getDisplayName(),
                    booking.getUser().getEmail(),
                    teamsByUser.getOrDefault(booking.getUser().getId(), List.of()),
                    status,
                    booking.getCheckedInAt()));
        }

        return new DayOccupancyReport(date, (int) seats.count(), booked, attended, noShows, cancelled, rows);
    }
}
