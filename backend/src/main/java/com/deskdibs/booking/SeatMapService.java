package com.deskdibs.booking;

import com.deskdibs.common.OfficeClock;
import com.deskdibs.layout.DeskTable;
import com.deskdibs.layout.Floor;
import com.deskdibs.layout.Zone;
import com.deskdibs.seat.FloorMapView;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatMapResponse;
import com.deskdibs.seat.SeatMapView;
import com.deskdibs.seat.SeatReservation;
import com.deskdibs.seat.SeatReservationRepository;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.seat.TableMapView;
import com.deskdibs.seat.ZoneMapView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the top-down floor map: the seat layout plus, for one date, who holds each seat and
 * whether a team hold still applies.
 *
 * <p>Lives in {@code booking} rather than {@code seat} deliberately. {@code booking} already
 * depends on {@code seat} — a booking is for a seat — so combining seat layout with booking and
 * reservation state here adds no new dependency direction. Building it in {@code seat} instead
 * would make that package depend back on {@code booking} for the occupant data every non-available
 * seat needs, turning one clean edge into a cycle.
 *
 * <h2>One round trip, however many seats exist</h2>
 * Three queries, none of them proportional to the seat count: the layout
 * ({@link SeatRepository#findAllWithLayoutOrdered()}), the day's bookings
 * ({@link BookingRepository#findByBookingDateAndStatusFetchUser}), and the day's holds
 * ({@link SeatReservationRepository#findActiveOnDateFetchTeam}). Everything else is an in-memory
 * join keyed by seat id — walking {@code seat.getDeskTable().getZone().getFloor()} costs nothing
 * further because the layout query already fetch-joined the whole chain.
 */
@Service
public class SeatMapService {

    private final SeatRepository seats;
    private final BookingRepository bookings;
    private final SeatReservationRepository reservations;
    private final OfficeClock officeClock;

    public SeatMapService(SeatRepository seats,
                          BookingRepository bookings,
                          SeatReservationRepository reservations,
                          OfficeClock officeClock) {
        this.seats = seats;
        this.bookings = bookings;
        this.reservations = reservations;
        this.officeClock = officeClock;
    }

    /** The whole map for one date, grouped floor → zone → table → seat in stable render order. */
    @Transactional(readOnly = true)
    public SeatMapResponse buildMap(LocalDate date) {
        List<Seat> orderedSeats = seats.findAllWithLayoutOrdered();

        Map<Long, Booking> bookingBySeatId = bookings.findByBookingDateAndStatusFetchUser(date, BookingStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(booking -> booking.getSeat().getId(), Function.identity()));

        Map<Long, List<SeatReservation>> reservationsBySeatId = reservations.findActiveOnDateFetchTeam(date)
                .stream()
                .collect(Collectors.groupingBy(reservation -> reservation.getSeat().getId()));

        Map<Long, FloorAccumulator> floors = new LinkedHashMap<>();
        for (Seat seat : orderedSeats) {
            DeskTable table = seat.getDeskTable();
            Zone zone = table.getZone();
            Floor floor = zone.getFloor();

            FloorAccumulator floorAcc = floors.computeIfAbsent(floor.getId(), id -> new FloorAccumulator(floor));
            ZoneAccumulator zoneAcc = floorAcc.zones.computeIfAbsent(zone.getId(), id -> new ZoneAccumulator(zone));
            TableAccumulator tableAcc = zoneAcc.tables.computeIfAbsent(table.getId(), id -> new TableAccumulator(table));

            tableAcc.seats.add(viewOf(seat, date, bookingBySeatId.get(seat.getId()),
                    reservationsBySeatId.getOrDefault(seat.getId(), List.of())));
        }

        List<FloorMapView> floorViews = floors.values().stream().map(FloorAccumulator::toView).toList();
        return new SeatMapResponse(date, floorViews);
    }

    /**
     * One seat's current state, for the WebSocket broadcast that follows a claim, move, cancel or
     * check-in. Deliberately re-queries rather than trusting whatever the caller computed before
     * its transaction committed, so the broadcast always reflects what is actually now true.
     */
    @Transactional(readOnly = true)
    public SeatMapView snapshotOf(long seatId, LocalDate date) {
        Seat seat = seats.findById(seatId).orElseThrow();
        Booking booking = bookings.findBySeatAndDateWithSeatAndUser(seatId, date, BookingStatus.ACTIVE).orElse(null);
        List<SeatReservation> holds = reservations
                .findBySeatIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(seatId, date, date);
        return viewOf(seat, date, booking, holds);
    }

    /**
     * The one rule shared by both call sites above: a disabled seat renders as disabled regardless
     * of any booking or hold; otherwise an active booking wins, then an unreleased team hold, and
     * failing both the seat is simply available.
     */
    private SeatMapView viewOf(Seat seat, LocalDate date, Booking activeBooking, List<SeatReservation> holds) {
        if (!seat.isBookable()) {
            return SeatMapView.disabled(seat);
        }
        if (activeBooking != null) {
            return SeatMapView.occupied(seat, activeBooking.getId(), activeBooking.getUser().getId(),
                    activeBooking.getUser().getDisplayName(), activeBooking.getCheckedInAt() != null);
        }
        SeatReservation enforcedHold = firstEnforcedHold(holds, date);
        if (enforcedHold != null) {
            return SeatMapView.teamReserved(seat, enforcedHold.getTeam().getId(), enforcedHold.getTeam().getName(),
                    enforcedHold.getReleaseAtTime());
        }
        return SeatMapView.available(seat);
    }

    /**
     * Same soft-release rule as {@code BookingService#requireNotHeldForAnotherTeam}: past a hold's
     * release time on the booked date, it simply stops being enforced — no job, no state change.
     */
    private SeatReservation firstEnforcedHold(List<SeatReservation> holds, LocalDate date) {
        for (SeatReservation hold : holds) {
            if (officeClock.isBefore(date, hold.getReleaseAtTime())) {
                return hold;
            }
        }
        return null;
    }

    private static final class FloorAccumulator {
        private final Floor floor;
        private final Map<Long, ZoneAccumulator> zones = new LinkedHashMap<>();

        private FloorAccumulator(Floor floor) {
            this.floor = floor;
        }

        private FloorMapView toView() {
            return new FloorMapView(floor.getId(), floor.getName(),
                    zones.values().stream().map(ZoneAccumulator::toView).toList());
        }
    }

    private static final class ZoneAccumulator {
        private final Zone zone;
        private final Map<Long, TableAccumulator> tables = new LinkedHashMap<>();

        private ZoneAccumulator(Zone zone) {
            this.zone = zone;
        }

        private ZoneMapView toView() {
            return new ZoneMapView(zone.getId(), zone.getName(),
                    tables.values().stream().map(TableAccumulator::toView).toList());
        }
    }

    private static final class TableAccumulator {
        private final DeskTable table;
        private final List<SeatMapView> seats = new ArrayList<>();

        private TableAccumulator(DeskTable table) {
            this.table = table;
        }

        private TableMapView toView() {
            return new TableMapView(table.getId(), table.getLabel(), table.getCapacity(),
                    table.getPosX(), table.getPosY(), table.getRotation(), List.copyOf(seats));
        }
    }
}
