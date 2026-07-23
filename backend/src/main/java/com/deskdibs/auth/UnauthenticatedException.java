package com.deskdibs.auth;

import java.io.Serial;

/**
 * Something asked for the current user on a request that has none.
 *
 * <p>Normally unreachable: the filter chain denies by default, so a handler only runs once a
 * caller is authenticated. It exists for the case that is not normal — an endpoint added later and
 * accidentally permitted — where failing closed with a 401 is the only acceptable outcome. The
 * alternative, a null user flowing into a service, would authorize a mutation against nobody.
 */
public class UnauthenticatedException extends AuthException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PUBLIC_MESSAGE = "Authentication is required.";

    public UnauthenticatedException(String logReason) {
        super(AuthErrorCode.UNAUTHENTICATED, "Unauthenticated: " + logReason, PUBLIC_MESSAGE);
    }
}
