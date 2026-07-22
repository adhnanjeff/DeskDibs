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
 * The interim 110-seat layout is seeded exactly. If this drifts, the floor map silently
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
    @DisplayName("the seed produces exactly 110 seats, 50 on the left and 60 on the right")
    void theSeedProducesExactlyOneHundredAndTenSeats() {
        assertThat(seatRepository.count()).isEqualTo(110);
        assertThat(seatRepository.countByDeskTableZoneId(zone("Left Wing").getId())).isEqualTo(50);
        assertThat(seatRepository.countByDeskTableZoneId(zone("Right Wing").getId())).isEqualTo(60);
    }

    @Test
    @DisplayName("the seed produces 18 tables: L1-L7 seat six, L8 seats eight, R1-R10 seat six")
    void theSeedProducesEighteenTablesWithTheExpectedCapacities() {
        assertThat(deskTableRepository.count()).isEqualTo(18);

        Map<String, Integer> capacityByLabel = deskTableRepository.findAll().stream()
                .collect(Collectors.toMap(DeskTable::getLabel, DeskTable::getCapacity));

        assertThat(capacityByLabel).hasSize(18);
        for (int i = 1; i <= 7; i++) {
            assertThat(capacityByLabel).containsEntry("L" + i, 6);
        }
        assertThat(capacityByLabel).containsEntry("L8", 8);
        for (int i = 1; i <= 10; i++) {
            assertThat(capacityByLabel).containsEntry("R" + i, 6);
        }

        assertThat(deskTableRepository.countByZoneId(zone("Left Wing").getId())).isEqualTo(8);
        assertThat(deskTableRepository.countByZoneId(zone("Right Wing").getId())).isEqualTo(10);
    }

    @Test
    @DisplayName("every seat label is unique, well formed, and agrees with its table, side and index")
    void everySeatLabelIsUniqueAndWellFormed() {
        List<Seat> seats = seatRepository.findAll();

        assertThat(seats).hasSize(110);
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
                    .isBetween(1, seat.getDeskTable().getCapacity() / 2);
            assertThat(seat.getStatus()).isEqualTo(SeatStatus.ACTIVE);
        }
    }

    @Test
    @DisplayName("each table carries exactly its capacity in seats, split evenly across sides A and B")
    void eachTableCarriesExactlyItsCapacityInSeats() {
        for (DeskTable table : deskTableRepository.findAll()) {
            List<Seat> seats = seatRepository.findByDeskTableId(table.getId());

            assertThat(seats)
                    .as("table %s should seat %d", table.getLabel(), table.getCapacity())
                    .hasSize(table.getCapacity());
            assertThat(seats.stream().filter(s -> s.getSide().name().equals("A")).count())
                    .isEqualTo(table.getCapacity() / 2);
            assertThat(seats.stream().filter(s -> s.getSide().name().equals("B")).count())
                    .isEqualTo(table.getCapacity() / 2);
        }
    }

    @Test
    @DisplayName("the two wings are laid out on a grid with a central aisle between them")
    void theTwoWingsAreLaidOutOnAGridWithACentralAisle() {
        List<DeskTable> left = deskTableRepository.findByZoneId(zone("Left Wing").getId());
        List<DeskTable> right = deskTableRepository.findByZoneId(zone("Right Wing").getId());

        assertThat(left).extracting(DeskTable::getPosX).containsOnly(80, 340);
        assertThat(left).extracting(DeskTable::getPosY).containsOnly(80, 260, 440, 620);
        assertThat(right).extracting(DeskTable::getPosX).containsOnly(800, 1060);
        assertThat(right).extracting(DeskTable::getPosY).containsOnly(80, 260, 440, 620, 800);

        int rightmostLeftWing = left.stream().mapToInt(DeskTable::getPosX).max().orElseThrow();
        int leftmostRightWing = right.stream().mapToInt(DeskTable::getPosX).min().orElseThrow();
        assertThat(leftmostRightWing - rightmostLeftWing)
                .as("a central aisle separates the wings")
                .isGreaterThan(260);
    }

    private Floor mainFloor() {
        return floorRepository.findByName("Main Floor").orElseThrow();
    }

    private Zone zone(String name) {
        return zoneRepository.findByFloorIdAndName(mainFloor().getId(), name).orElseThrow();
    }
}
