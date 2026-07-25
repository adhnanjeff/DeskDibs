package com.deskdibs.layout;

import com.deskdibs.common.AbstractPostgresIntegrationTest;
import com.deskdibs.seat.Seat;
import com.deskdibs.seat.SeatRepository;
import com.deskdibs.seat.SeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The finalized 102-seat layout is seeded exactly (V2 seeds the interim office, V4 repositions
 * it into two bands, V5 shrinks R10 and L8 to 3-seaters). If this drifts, the floor map silently
 * misrepresents the office, so the counts are asserted literally rather than derived.
 */
@Transactional(readOnly = true)
class SeatLayoutSeedTest extends AbstractPostgresIntegrationTest {

    private static final Pattern SEAT_LABEL = Pattern.compile("^[LR](\\d+)-([AB])(\\d+)$");

    private final FloorRepository floorRepository;
    private final ZoneRepository zoneRepository;
    private final DeskTableRepository deskTableRepository;
    private final SeatRepository seatRepository;

    SeatLayoutSeedTest(FloorRepository floorRepository,
                       ZoneRepository zoneRepository,
                       DeskTableRepository deskTableRepository,
                       SeatRepository seatRepository) {
        this.floorRepository = floorRepository;
        this.zoneRepository = zoneRepository;
        this.deskTableRepository = deskTableRepository;
        this.seatRepository = seatRepository;
    }

    @Test
    @DisplayName("the office seeds one floor with a Left Wing and a Right Wing")
    void theOfficeSeedsOneFloorWithALeftWingAndARightWing() {
        Floor mainFloor = mainFloor();

        assertThat(floorRepository.findAll()).hasSize(1);
        assertThat(zoneRepository.findByFloorIdOrderByDisplayOrderAsc(mainFloor.getId()))
                .extracting(Zone::getName)
                .containsExactly("Left Wing", "Right Wing");
    }

    @Test
    @DisplayName("the seed produces exactly 102 seats, 45 on the left and 57 on the right")
    void theSeedProducesExactlyOneHundredAndTwoSeats() {
        assertThat(seatRepository.count()).isEqualTo(102);
        assertThat(seatRepository.countByDeskTableZoneId(zone("Left Wing").getId())).isEqualTo(45);
        assertThat(seatRepository.countByDeskTableZoneId(zone("Right Wing").getId())).isEqualTo(57);
    }

    @Test
    @DisplayName("the seed produces 18 tables: R10 and L8 seat three, every other table seats six")
    void theSeedProducesEighteenTablesWithTheExpectedCapacities() {
        assertThat(deskTableRepository.count()).isEqualTo(18);

        Map<String, Integer> capacityByLabel = deskTableRepository.findAll().stream()
                .collect(Collectors.toMap(DeskTable::getLabel, DeskTable::getCapacity));

        assertThat(capacityByLabel).hasSize(18);
        // R10 (upper-band right end) and L8 (lower-band right end) are the two 3-seaters.
        assertThat(capacityByLabel).containsEntry("R10", 3);
        assertThat(capacityByLabel).containsEntry("L8", 3);
        for (int i = 1; i <= 7; i++) {
            assertThat(capacityByLabel).containsEntry("L" + i, 6);
        }
        for (int i = 1; i <= 9; i++) {
            assertThat(capacityByLabel).containsEntry("R" + i, 6);
        }

        assertThat(deskTableRepository.countByZoneId(zone("Left Wing").getId())).isEqualTo(8);
        assertThat(deskTableRepository.countByZoneId(zone("Right Wing").getId())).isEqualTo(10);
    }

    @Test
    @DisplayName("every seat label is unique, well formed, and agrees with its table, side and index")
    void everySeatLabelIsUniqueAndWellFormed() {
        List<Seat> seats = seatRepository.findAll();

        assertThat(seats).hasSize(102);
        assertThat(seats).extracting(Seat::getLabel).doesNotHaveDuplicates();

        for (Seat seat : seats) {
            assertThat(seat.getLabel())
                    .as("seat label should read like R3-A2")
                    .matches(SEAT_LABEL);

            String expected = seat.getDeskTable().getLabel() + "-" + seat.getSide() + seat.getSeatIndex();
            assertThat(seat.getLabel())
                    .as("label must be derivable from table, side and index")
                    .isEqualTo(expected);

            assertThat(seat.getSeatIndex())
                    .as("seat %s sits within its side of the table", seat.getLabel())
                    .isBetween(1, (seat.getDeskTable().getCapacity() + 1) / 2);
            assertThat(seat.getStatus()).isEqualTo(SeatStatus.ACTIVE);
        }
    }

    @Test
    @DisplayName("each table carries exactly its capacity: side A takes the odd seat on the 3-seaters")
    void eachTableCarriesExactlyItsCapacityInSeats() {
        for (DeskTable table : deskTableRepository.findAll()) {
            List<Seat> seats = seatRepository.findByDeskTableId(table.getId());
            int capacity = table.getCapacity();

            assertThat(seats)
                    .as("table %s should seat %d", table.getLabel(), capacity)
                    .hasSize(capacity);
            // Even tables split evenly; an odd 3-seater puts the extra seat on side A.
            assertThat(seats.stream().filter(s -> s.getSide().name().equals("A")).count())
                    .as("side A of %s", table.getLabel())
                    .isEqualTo((capacity + 1) / 2);
            assertThat(seats.stream().filter(s -> s.getSide().name().equals("B")).count())
                    .as("side B of %s", table.getLabel())
                    .isEqualTo(capacity / 2);
        }
    }

    @Test
    @DisplayName("V4 repositions the workstations into two bands: R1-R10 up top, L1-L8 below")
    void theWorkstationsAreRepositionedIntoTwoBands() {
        // After V4 the tables carry logical GRID indices (pos_y = band, pos_x = column
        // within the band) that the front-end resolves into pixels. The Right Wing (R1-R10)
        // becomes the upper band, the Left Wing (L1-L8) the lower one.
        List<DeskTable> upper = deskTableRepository.findByZoneId(zone("Right Wing").getId());
        List<DeskTable> lower = deskTableRepository.findByZoneId(zone("Left Wing").getId());

        assertThat(upper).extracting(DeskTable::getPosY).containsOnly(0);
        assertThat(upper).extracting(DeskTable::getPosX)
                .containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        assertThat(lower).extracting(DeskTable::getPosY).containsOnly(1);
        assertThat(lower).extracting(DeskTable::getPosX)
                .containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5, 6, 7);

        int upperBand = upper.stream().mapToInt(DeskTable::getPosY).max().orElseThrow();
        int lowerBand = lower.stream().mapToInt(DeskTable::getPosY).min().orElseThrow();
        assertThat(lowerBand)
                .as("the lower zone sits on a distinct band below the upper zone")
                .isGreaterThan(upperBand);
    }

    private Floor mainFloor() {
        return floorRepository.findByName("Main Floor").orElseThrow();
    }

    private Zone zone(String name) {
        return zoneRepository.findByFloorIdAndName(mainFloor().getId(), name).orElseThrow();
    }
}
