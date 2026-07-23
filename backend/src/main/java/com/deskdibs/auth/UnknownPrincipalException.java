package com.deskdibs.auth;

import java.io.Serial;

/**
 * The token survived signature, expiry, issuer and audience checks, and still names nobody.
 *
 * <p>Under the local provider that means the subject is not a user id this database holds — a
 * token outliving the account it was issued for. Under Entra it means the token carried no usable
 * object id or email, so there was nothing stable to provision against; inventing a user from a
 * token that will not identify the same person twice is worse than refusing.
 *
 * <p>Fails closed: a token this system cannot attach to a person grants nothing.
 */
public class UnknownPrincipalException extends AuthException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PUBLIC_MESSAGE = "This token does not identify a known user.";

    public UnknownPrincipalException(String logReason) {
        super(AuthErrorCode.UNKNOWN_PRINCIPAL, "Principal not resolvable: " + logReason, PUBLIC_MESSAGE);
    }
}
