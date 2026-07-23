package com.deskdibs.auth;

/**
 * A freshly minted access token and how long it lasts.
 *
 * @param value            the compact JWS. Never logged, never persisted — it is a bearer
 *                         credential, so a copy anywhere is a second key to the account
 * @param expiresInSeconds lifetime from issue, so a client can refresh before being refused
 */
public record IssuedToken(String value, long expiresInSeconds) {
}
