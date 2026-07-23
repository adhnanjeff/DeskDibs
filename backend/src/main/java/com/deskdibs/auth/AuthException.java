package com.deskdibs.auth;

import org.springframework.security.core.AuthenticationException;

import java.io.Serial;

/**
 * Base of every refusal this package can produce.
 *
 * <p>Extends Spring Security's {@link AuthenticationException} rather than a plain runtime
 * exception for one specific reason: exceptions of that type thrown anywhere inside the filter
 * chain — including from the JWT-to-principal converter — are caught by
 * {@code ExceptionTranslationFilter} and routed to the {@code AuthenticationEntryPoint}. That
 * turns "this token names a deactivated user" into a clean 401 JSON body instead of a 500 and a
 * stack trace.
 *
 * <p>Two messages live on each subclass, and the distinction matters. {@link #getMessage()} is for
 * the log: it may name the subject or the account. {@link #publicMessage()} is what crosses the
 * wire: fixed wording that reveals nothing about which accounts exist.
 */
public abstract class AuthException extends AuthenticationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final AuthErrorCode errorCode;
    private final String publicMessage;

    protected AuthException(AuthErrorCode errorCode, String logMessage, String publicMessage) {
        super(logMessage);
        this.errorCode = errorCode;
        this.publicMessage = publicMessage;
    }

    /** Stable identity of this failure, for mapping and for clients to branch on. */
    public AuthErrorCode errorCode() {
        return errorCode;
    }

    /** Safe to send to an unauthenticated caller. Never derived from {@link #getMessage()}. */
    public String publicMessage() {
        return publicMessage;
    }
}
