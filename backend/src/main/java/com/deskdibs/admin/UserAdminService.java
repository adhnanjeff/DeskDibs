package com.deskdibs.admin;

import com.deskdibs.booking.AdministrativeReleaseService;
import com.deskdibs.booking.BookingView;
import com.deskdibs.user.AppUser;
import com.deskdibs.user.AppUserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Account lifecycle: who is on the system, and whether their account is live (PLAN.md §5 #12).
 *
 * <p>Deactivation was already half-built before this class existed — {@code app_user.active} was
 * there and both auth providers refused a deactivated account at login. What was missing is the
 * other half of the edge case: <em>"future bookings released"</em>. Without it somebody who leaves
 * the company keeps holding a desk every working day for the next fortnight, and because the seat
 * renders as occupied nobody else can take it and nobody can see why.
 *
 * <p>Deactivating and releasing happen in one transaction, so an account can never end up refused at
 * login while still holding desks, nor stripped of its desks while still able to sign in.
 */
@Service
public class UserAdminService {

    private final AppUserRepository users;
    private final AdministrativeReleaseService releases;

    public UserAdminService(AppUserRepository users, AdministrativeReleaseService releases) {
        this.users = users;
        this.releases = releases;
    }

    /** Everyone on the system, ordered by name so the list is stable between refreshes. */
    @Transactional(readOnly = true)
    public List<AdminUserView> listUsers() {
        return users.findAll(Sort.by("displayName").ascending()).stream()
                .map(AdminUserView::of)
                .toList();
    }

    /**
     * Activate or deactivate {@code targetUserId}. Deactivating also hands back every desk that
     * account is still holding from today onward.
     *
     * @throws CannotDeactivateSelfException  the caller aimed this at their own account
     * @throws AdminUserNotFoundException     no user with that id
     */
    @Transactional
    public UserActivationReport setActive(long targetUserId, boolean active, long actingUserId) {
        if (!active && targetUserId == actingUserId) {
            throw new CannotDeactivateSelfException(actingUserId);
        }

        AppUser user = users.findById(targetUserId)
                .orElseThrow(() -> new AdminUserNotFoundException(targetUserId));

        // Idempotent: asking for the state it is already in is a no-op rather than a second
        // release pass, so a double-clicked button cannot report the same desks twice.
        if (user.isActive() == active) {
            return new UserActivationReport(user.getId(), user.getDisplayName(), active,
                    true, 0, List.of());
        }

        user.setActive(active);
        users.saveAndFlush(user);

        // Reactivating gives nothing back. The desks are gone, and silently re-claiming them on
        // somebody's behalf would be worse than making them book again — by now other people may
        // well be sitting there.
        List<BookingView> released = active ? List.of() : releases.releaseEverythingHeldBy(targetUserId);

        return new UserActivationReport(user.getId(), user.getDisplayName(), active, false,
                released.size(), released.stream().map(ReleasedBookingView::of).toList());
    }
}
