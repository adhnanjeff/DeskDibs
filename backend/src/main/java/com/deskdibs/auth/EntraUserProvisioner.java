package com.deskdibs.auth;

import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import com.deskdibs.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.Optional;

/**
 * Just-in-time provisioning: the first time somebody signs in through Entra, they get a row.
 *
 * <p>Without this, using DeskDibs would require an administrator to pre-create an account for
 * every employee and keep the list in step with joiners and leavers — a second directory
 * maintained by hand alongside the one the company already has. Instead the tenant stays
 * authoritative about who exists, and this table holds only what the tenant does not know: which
 * seat somebody booked, and what they may do in this application.
 *
 * <h2>The rule that matters</h2>
 * A provisioned user is always {@link UserRole#EMPLOYEE}. The token may assert {@code roles} or
 * {@code groups} claiming otherwise and none of it is read here — not as a hint, not as a default.
 * If a token could name the role of the row it creates, then anybody who could get a token could
 * mint themselves an administrator, and every other control in this package would be decoration.
 * Promotion is an act somebody performs against the database, deliberately.
 *
 * <h2>Matching, in order</h2>
 * <ol>
 *   <li>{@code external_id} equals the token's {@code oid} — the normal path, every sign-in after
 *       the first;</li>
 *   <li>otherwise an existing row with that email and no {@code external_id} yet: an account
 *       created before SSO was switched on, adopted rather than duplicated, keeping its bookings
 *       and its role;</li>
 *   <li>otherwise a new row.</li>
 * </ol>
 * An email that already belongs to a <em>different</em> {@code oid} stops at
 * {@link IdentityConflictException} rather than being resolved by guesswork — see that class.
 *
 * <h2>Two first sign-ins at once</h2>
 * Handled the way the rest of this codebase handles a race: the insert goes in, and
 * {@code uq_app_user_external_id} decides. The loser reads back the winner's row and carries on,
 * so the user never sees an error for having two browser tabs. The recovery deliberately runs
 * <em>after</em> the failed transaction has rolled back — a PostgreSQL transaction aborted by a
 * constraint violation cannot answer another query, and re-reading in a nested transaction would
 * make a losing thread hold two connections at once. Same reasoning as {@code BookingService}.
 */
@Component
@ConditionalOnProperty(name = AuthProviderKind.PROPERTY, havingValue = "entra")
public class EntraUserProvisioner {

    private static final Logger log = LoggerFactory.getLogger(EntraUserProvisioner.class);

    private final AppUserRepository users;
    private final TransactionTemplate transactionTemplate;

    public EntraUserProvisioner(AppUserRepository users, TransactionTemplate transactionTemplate) {
        this.users = users;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * The {@code app_user} row this identity means, creating it if this is the first sight of it.
     *
     * @throws UnknownPrincipalException the token carries neither an object id nor an email, so
     *                                   there is nothing stable to provision against
     * @throws IdentityConflictException the email belongs to somebody with a different object id
     */
    public AppUser resolve(EntraIdentity identity) {
        if (!identity.hasExternalId()) {
            return withoutObjectId(identity);
        }

        Optional<AppUser> known = users.findByExternalId(identity.externalId());
        if (known.isPresent()) {
            return refreshDisplayName(known.get(), identity);
        }

        Optional<AppUser> byEmail = identity.hasEmail()
                ? users.findByEmailIgnoreCase(identity.email())
                : Optional.empty();
        if (byEmail.isPresent()) {
            return adopt(byEmail.get(), identity);
        }

        return provision(identity);
    }

    /**
     * No {@code oid}: an existing account may still be matched by email, but nothing is created.
     *
     * <p>Provisioning needs an identifier that will name the same person tomorrow. An email alone
     * is not that — addresses get recycled — so a row keyed on one would silently become the wrong
     * person's. Refusing is the honest outcome, and the fix is a tenant-side configuration change
     * to emit the optional claim.
     */
    private AppUser withoutObjectId(EntraIdentity identity) {
        if (!identity.hasEmail()) {
            throw new UnknownPrincipalException("Entra token carries neither an oid nor an email claim");
        }
        return users.findByEmailIgnoreCase(identity.email())
                .orElseThrow(() -> new UnknownPrincipalException(
                        "Entra token carries no oid claim, and no existing account matches its email"));
    }

    /** Adopt a pre-SSO account: attach the object id, keep everything else, including the role. */
    private AppUser adopt(AppUser existing, EntraIdentity identity) {
        if (existing.getExternalId() != null && !existing.getExternalId().equals(identity.externalId())) {
            throw new IdentityConflictException(identity.externalId(), identity.email(), existing.getId());
        }
        log.info("Entra auth: linking existing user {} to external id {}",
                existing.getId(), identity.externalId());
        return transactionTemplate.execute(status -> {
            AppUser user = users.findById(existing.getId()).orElseThrow();
            user.setExternalId(identity.externalId());
            return users.saveAndFlush(user);
        });
    }

    private AppUser provision(EntraIdentity identity) {
        try {
            AppUser created = transactionTemplate.execute(status -> {
                AppUser user = new AppUser(identity.email(), identity.displayNameOrEmail(), UserRole.EMPLOYEE);
                user.setExternalId(identity.externalId());
                return users.saveAndFlush(user);
            });
            log.info("Entra auth: provisioned user {} on first sign-in", Objects.requireNonNull(created).getId());
            return created;
        } catch (DataIntegrityViolationException lostTheRace) {
            return whoeverWon(identity, lostTheRace);
        }
    }

    /**
     * Another request provisioned the same person a moment ago. Read their row and use it — a
     * user opening two tabs has done nothing wrong and should not see an error for it.
     */
    private AppUser whoeverWon(EntraIdentity identity, DataIntegrityViolationException violation) {
        return users.findByExternalId(identity.externalId())
                // Not the external-id index, then: an email collision this class already ruled out
                // on the way in, or a constraint that is nothing to do with provisioning. Dressing
                // either up as a race would hide a real defect.
                .orElseThrow(() -> violation);
    }

    /**
     * Keep the display name in step with the tenant, and nothing else.
     *
     * <p>Only when it actually changed, so the usual path stays one indexed read and no write. The
     * email is deliberately left frozen: changing it can collide with {@code uq_app_user_email},
     * and an authentication filter is the worst possible place to discover that. A genuine address
     * change is an administrative action, not something to attempt mid-request.
     */
    private AppUser refreshDisplayName(AppUser user, EntraIdentity identity) {
        String current = identity.displayNameOrEmail();
        if (current.isEmpty() || current.equals(user.getDisplayName())) {
            return user;
        }
        return transactionTemplate.execute(status -> {
            AppUser managed = users.findById(user.getId()).orElseThrow();
            managed.setDisplayName(current);
            return users.saveAndFlush(managed);
        });
    }
}
