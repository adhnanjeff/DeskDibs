package com.deskdibs.auth;

import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Email and password against {@code app_user}, for development only.
 *
 * <h2>Every failure looks the same</h2>
 * Unknown address, wrong password, an Entra account with no password set, a deactivated account —
 * all four leave as one {@link InvalidCredentialsException} with one message. A login endpoint
 * that answers "no such user" differently from "wrong password" is a staff directory: point it at
 * a list of plausible addresses and it tells you which ones are real. One that distinguishes
 * "deactivated" additionally announces who has just left the company.
 *
 * <h2>And takes the same time</h2>
 * Same responses are not enough on their own. If an unknown address returned immediately while a
 * known one spent 60ms inside BCrypt, the clock would answer the question the response body
 * refused to. So the hash comparison runs on every attempt: against the stored hash when there is
 * one, and against a decoy otherwise. The decoy is generated at startup from a random string
 * nobody — including this process, after the constructor returns — knows the plaintext of, so it
 * is a real BCrypt verification of the same cost and not a hardcoded credential.
 *
 * <p>This is a mitigation, not a proof: BCrypt's cost dominates, but the surrounding database read
 * still differs slightly. The remaining signal is far below the noise of a network round trip, and
 * closing it entirely is the rate limiter's job in a later phase.
 */
@Service
@ConditionalOnProperty(name = AuthProviderKind.PROPERTY, havingValue = "local")
public class LocalLoginService {

    private static final Logger log = LoggerFactory.getLogger(LocalLoginService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final LocalTokenIssuer tokenIssuer;

    /** A valid BCrypt hash of a value that was discarded the moment it was hashed. */
    private final String decoyHash;

    public LocalLoginService(AppUserRepository users,
                             PasswordEncoder passwordEncoder,
                             LocalTokenIssuer tokenIssuer) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.decoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * @throws InvalidCredentialsException any refusal at all, wording identical in every case
     */
    @Transactional(readOnly = true)
    public LoginResponse login(String email, String rawPassword) {
        AppUser user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        String storedHash = user == null ? null : user.getPasswordHash();

        // Unconditional, and before any branch on `user`: the work must not depend on the answer.
        boolean passwordMatches = passwordEncoder.matches(
                rawPassword, storedHash == null ? decoyHash : storedHash);

        if (user == null) {
            // The submitted address is deliberately not logged. People type their password into
            // the email field often enough that echoing that field would put plaintext passwords
            // in the log, which is a far worse outcome than a slightly less specific audit line.
            throw refuse("no app_user with the submitted email");
        }
        if (storedHash == null) {
            throw refuse("user " + user.getId() + " has no local password (Entra-only account)");
        }
        if (!passwordMatches) {
            throw refuse("wrong password for user " + user.getId());
        }
        if (!user.isActive()) {
            throw refuse("user " + user.getId() + " is deactivated");
        }

        log.info("Local login succeeded for user {}", user.getId());
        return LoginResponse.of(tokenIssuer.issue(user),
                CurrentUserResponse.of(AuthenticatedUser.of(user), AuthProviderKind.LOCAL));
    }

    /** The reason goes to the log, where an operator can see it. It never goes to the caller. */
    private InvalidCredentialsException refuse(String reason) {
        log.info("Local login refused: {}", reason);
        return new InvalidCredentialsException(reason);
    }
}
