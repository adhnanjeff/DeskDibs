package com.deskdibs.auth;

import com.deskdibs.common.AbstractPostgresIntegrationTest;
import com.deskdibs.common.ControllableClockConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Base for the tests that drive authentication through the real filter chain.
 *
 * <p>{@code @AutoConfigureMockMvc} is what makes these worth writing: the request goes through
 * {@code springSecurityFilterChain}, so what is under test is the wiring — the matchers, the
 * decoder, the principal converter, the entry point — and not a service called directly with the
 * security layer imagined.
 *
 * <p>The controllable clock is imported because token expiry is a rule about time, and the only
 * honest way to test one is to control it. The alternative, issuing a token with a lifetime of a
 * second and sleeping, tests the test runner's scheduling as much as the decoder.
 *
 * <p>Public rather than package-private since Phase 4: every feature controller test — booking,
 * reservations, the seat map — needs this exact harness (MockMvc through the real security chain,
 * a movable clock) to log in and call an endpoint as a real user. Duplicating it per package would
 * only give three copies a chance to drift; {@link AbstractPostgresIntegrationTest}, its own
 * parent, already made the same choice for the same reason.
 */
@AutoConfigureMockMvc
@Import({ControllableClockConfiguration.class, AdminOnlyTestEndpoint.class})
public abstract class AbstractAuthWebTest extends AbstractPostgresIntegrationTest {
}
