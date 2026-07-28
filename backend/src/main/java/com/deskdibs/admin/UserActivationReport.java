package com.deskdibs.admin;

import java.util.List;

/**
 * What actually happened when an account was activated or deactivated.
 *
 * <p>The released list is the point. Deactivating somebody quietly frees every desk they were
 * holding, and an administrator who only saw <em>"done"</em> would have no idea that they had just
 * cancelled four people's plans — or, more usefully, that a desk they were looking for has just come
 * back into the pool.
 *
 * @param wasAlreadyInThatState the request asked for the state the account was already in, so
 *                              nothing was changed and nothing released
 */
public record UserActivationReport(
        Long userId,
        String displayName,
        boolean active,
        boolean wasAlreadyInThatState,
        int bookingsReleased,
        List<ReleasedBookingView> released) {
}
