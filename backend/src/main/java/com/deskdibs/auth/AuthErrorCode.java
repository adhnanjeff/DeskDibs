package com.deskdibs.auth;

/**
 * Stable machine-readable identity of an authentication or authorization failure.
 *
 * <p>Deliberately shaped like {@code BookingErrorCode}: a screaming-snake enum a client branches
 * on without parsing an English sentence. Phase 4 unifies the two into one wire contract, so the
 * same rule applies here — names are part of the API: add values, never rename or repurpose one.
 *
 * <p>The values are chosen so that <em>none of them distinguishes one account from another</em>.
 * {@link #INVALID_CREDENTIALS} covers a wrong password, an unknown email, an account with no
 * password set, and a deactivated account alike, precisely so a login endpoint cannot be used to
 * enumerate who works here.
 */
public enum AuthErrorCode {

    /** No credentials were presented at all, and the endpoint requires them. */
    UNAUTHENTICATED,

    /** A bearer token was presented but is malformed, expired, or badly signed. */
    INVALID_TOKEN,

    /** Login was refused. Says nothing about which half of the pair was wrong. */
    INVALID_CREDENTIALS,

    /** The token is genuine and its subject exists, but the account has been deactivated. */
    USER_DEACTIVATED,

    /** The token is genuine but names nobody this system knows, and none could be provisioned. */
    UNKNOWN_PRINCIPAL,

    /** The token's external id and email point at two different people. Refused, never guessed. */
    IDENTITY_CONFLICT,

    /** Authenticated, but this role may not do that. */
    ACCESS_DENIED,

    /** The request body failed validation. */
    VALIDATION_FAILED
}
