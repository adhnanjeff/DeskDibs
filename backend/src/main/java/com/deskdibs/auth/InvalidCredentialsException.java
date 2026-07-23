package com.deskdibs.auth;

import java.io.Serial;

/**
 * Local login refused.
 *
 * <p>One exception for four different causes — unknown email, wrong password, an account that has
 * no password because it belongs to Entra, and a deactivated account — because the caller must not
 * be able to tell them apart. A distinct "no such user" would turn the login endpoint into a
 * directory of who works here, and a distinct "account disabled" would say who has just left.
 *
 * <p>The log message carries the real reason; {@link #publicMessage()} never does.
 */
public class InvalidCredentialsException extends AuthException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PUBLIC_MESSAGE = "Invalid email or password.";

    public InvalidCredentialsException(String logReason) {
        super(AuthErrorCode.INVALID_CREDENTIALS, "Login refused: " + logReason, PUBLIC_MESSAGE);
    }
}
