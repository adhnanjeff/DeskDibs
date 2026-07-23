package com.deskdibs.common;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migrations apply cleanly to an empty database, and the objects that carry the core invariant
 * really exist afterwards.
 *
 * <p>The Spring context under test runs with {@code ddl-auto: validate}, so the mere fact that
 * this class starts also proves every JPA mapping matches the Flyway-built schema.
 */
class SchemaMigrationTest extends AbstractPostgresIntegrationTest {

    private final Flyway flyway;
    private final JdbcTemplate jdbc;

    SchemaMigrationTest(Flyway flyway, JdbcTemplate jdbc) {
        this.flyway = flyway;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("every migration applies from scratch and reports success")
    void everyMigrationAppliesFromScratchAndReportsSuccess() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied)
                .as("V1, V2 and V3 should have run")
                .hasSize(3);
        assertThat(applied)
                .extracting(info -> info.getVersion().toString())
                .containsExactly("1", "2", "3");
        assertThat(Arrays.stream(applied).map(MigrationInfo::getState))
                .allMatch(MigrationState.SUCCESS::equals);
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("3");
    }

    @Test
    @DisplayName("every table in the data model exists")
    void everyTableInTheDataModelExists() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);

        assertThat(tables).contains(
                "app_user", "team", "team_member",
                "floor", "zone", "desk_table", "seat",
                "booking", "seat_reservation");
    }

    @Test
    @DisplayName("the uniqueness guards are partial indexes restricted to ACTIVE bookings")
    void theUniquenessGuardsArePartialIndexesRestrictedToActiveBookings() {
        // A plain unique index would also make the first insert win — and would then wrongly
        // block re-booking a cancelled seat. The WHERE clause is the part that must be right.
        // Postgres normalises the predicate to ((status)::text = 'ACTIVE'::text), hence the regex.
        assertThat(indexDefinition("uq_seat_active_per_date"))
                .containsIgnoringCase("UNIQUE INDEX")
                .contains("seat_id", "booking_date")
                .matches("(?s).*WHERE .*status.*'ACTIVE'.*");

        assertThat(indexDefinition("uq_user_active_per_date"))
                .containsIgnoringCase("UNIQUE INDEX")
                .contains("user_id", "booking_date")
                .matches("(?s).*WHERE .*status.*'ACTIVE'.*");

        assertThat(indexDefinition("uq_booking_idempotency"))
                .containsIgnoringCase("UNIQUE INDEX")
                .contains("idempotency_key")
                .matches("(?s).*WHERE .*idempotency_key IS NOT NULL.*");
    }

    @Test
    @DisplayName("the suite runs on a real PostgreSQL 18, not an in-memory stand-in")
    void theSuiteRunsOnARealPostgres18() {
        // Partial unique indexes are the whole design. H2 does not reproduce them, so a suite
        // that quietly fell back to one would prove nothing.
        String version = jdbc.queryForObject("SELECT version()", String.class);

        assertThat(version).startsWith("PostgreSQL 18");
    }

    private String indexDefinition(String indexName) {
        List<String> definitions = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                String.class, indexName);
        assertThat(definitions).as("index %s should exist", indexName).hasSize(1);
        return definitions.getFirst();
    }
}
