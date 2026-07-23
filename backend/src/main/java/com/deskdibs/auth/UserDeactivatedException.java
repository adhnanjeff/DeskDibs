package com.deskdibs.auth;

import java.io.Serial;

/**
 * A structurally valid, unexpired, correctly signed token belonging to a deactivated account.
 *
 * <p>PLAN.md §5 case 12: when somebody leaves, their future bookings are released and they are
 * refused at sign-in. Deactivation would be worthless if a token minted five minutes earlier kept
 * working until it expired, so {@code app_user.active} is re-read on <em>every</em> request rather
 * than baked into the token. Revocation is immediate and costs one indexed primary-key read.
 *
 * <p>Unlike a login refusal this one names its cause to the caller. The holder of a valid token
 * for an account already knows that account exists, so there is no enumeration to protect against,
 * and "your access was revoked" is far more useful than "invalid token".
 */
public class UserDeactivatedException extends AuthException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PUBLIC_MESSAGE = "This account has been deactivated.";

    public UserDeactivatedException(long userId) {
        super(AuthErrorCode.USER_DEACTIVATED,
                "User " + userId + " is deactivated and may not authenticate",
                PUBLIC_MESSAGE);
    }
}
