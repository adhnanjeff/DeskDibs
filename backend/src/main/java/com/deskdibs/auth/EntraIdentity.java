package com.deskdibs.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The three facts DeskDibs takes from an Entra token, and the only three.
 *
 * <p>Extracting them into a record before provisioning keeps {@link EntraUserProvisioner} a
 * function of plain data — testable without constructing a token — and, more importantly, makes
 * the boundary visible. Everything the tenant might also have put in the token, {@code roles} and
 * {@code groups} and {@code wids} chief among them, is dropped here, at one readable line, rather
 * than being available downstream to whoever reaches for it next.
 *
 * @param externalId the {@code oid} claim: the user's object id, stable for the life of the
 *                   account and unique across the tenant. Stored as {@code app_user.external_id}.
 *                   May be empty when the tenant has not configured the optional claim
 * @param email      the {@code email} claim, falling back to {@code preferred_username} and then
 *                   {@code upn}. Which of the three is present depends on tenant configuration,
 *                   so all three are tried
 * @param name       the {@code name} claim, for display
 */
public record EntraIdentity(String externalId, String email, String name) {

    private static final String OID_CLAIM = "oid";

    /** In order of preference. {@code preferred_username} is the one Entra populates by default. */
    private static final String[] EMAIL_CLAIMS = {"email", "preferred_username", "upn"};

    private static final String NAME_CLAIM = "name";

    public EntraIdentity {
        externalId = trimmed(externalId);
        email = trimmed(email);
        name = trimmed(name);
    }

    public static EntraIdentity from(Jwt token) {
        return new EntraIdentity(
                token.getClaimAsString(OID_CLAIM),
                firstPresent(token, EMAIL_CLAIMS),
                token.getClaimAsString(NAME_CLAIM));
    }

    public boolean hasExternalId() {
        return !externalId.isEmpty();
    }

    public boolean hasEmail() {
        return !email.isEmpty();
    }

    /** A display name that is always something, so {@code display_name NOT NULL} always holds. */
    public String displayNameOrEmail() {
        return name.isEmpty() ? email : name;
    }

    private static String firstPresent(Jwt token, String... claims) {
        for (String claim : claims) {
            String value = token.getClaimAsString(claim);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
