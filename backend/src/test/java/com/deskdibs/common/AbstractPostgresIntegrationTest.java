package com.deskdibs.common;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for every integration test: a real PostgreSQL 18, migrated by Flyway, with
 * Hibernate validating its mappings against the result.
 *
 * <p>Real Postgres rather than H2 is not a preference. The anti-double-booking guarantee
 * <em>is</em> a Postgres partial unique index; H2 does not reproduce one, so an H2-backed suite
 * would go green while proving nothing about the one behaviour this system must never get wrong.
 *
 * <p>The container is a {@code static} field, so it starts once and is shared by every test class
 * that reuses this Spring context instead of once per class. {@code @ServiceConnection} points the
 * datasource at it, overriding the placeholder values in {@code application-test.yml}.
 *
 * <p>{@code @TestConstructor} lets subclasses take their collaborators as constructor parameters,
 * keeping tests to the same constructor-injection rule as production code.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));
}
