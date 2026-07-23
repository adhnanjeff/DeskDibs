package com.deskdibs.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * The HMAC key the local provider signs and verifies its own tokens with.
 *
 * <p>Two sources, and no third. Either {@code DESKDIBS_JWT_SECRET} supplies one, or a random
 * 256-bit key is generated at startup. There is deliberately no fallback constant: a signing key
 * committed to a repository is a signing key anybody can forge tokens with, and one that ships as
 * a default is worse, because every deployment that never got round to setting the variable shares
 * it.
 *
 * <p>Generation is a real fallback, not a silent one — it logs what happened and what it costs
 * (tokens die with the process, and a second instance behind a load balancer would reject the
 * first instance's tokens). What it never logs is the key.
 *
 * <p>HS256 requires at least 256 bits of key material, so a short secret is rejected at startup
 * rather than at the first login.
 */
public final class LocalJwtSigningKey {

    private static final Logger log = LoggerFactory.getLogger(LocalJwtSigningKey.class);

    /** HS256's block size. Anything shorter weakens the signature and Nimbus refuses it anyway. */
    private static final int MINIMUM_KEY_BYTES = 32;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKey key;

    private LocalJwtSigningKey(SecretKey key) {
        this.key = key;
    }

    public static LocalJwtSigningKey from(LocalAuthProperties properties) {
        return properties.hasConfiguredSecret()
                ? new LocalJwtSigningKey(fromConfiguredSecret(properties.jwtSecret()))
                : new LocalJwtSigningKey(generated());
    }

    public SecretKey key() {
        return key;
    }

    private static SecretKey fromConfiguredSecret(String secret) {
        byte[] material = secret.getBytes(StandardCharsets.UTF_8);
        if (material.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "DESKDIBS_JWT_SECRET is " + material.length + " bytes; HS256 needs at least "
                            + MINIMUM_KEY_BYTES + ". Generate one with: openssl rand -base64 48");
        }
        log.info("Local auth: signing tokens with the configured DESKDIBS_JWT_SECRET.");
        return new SecretKeySpec(material, HMAC_ALGORITHM);
    }

    private static SecretKey generated() {
        byte[] material = new byte[MINIMUM_KEY_BYTES];
        new SecureRandom().nextBytes(material);
        log.warn("""
                Local auth: DESKDIBS_JWT_SECRET is not set, so a random signing key was generated \
                for this process. Every token issued now becomes invalid when the application \
                restarts, and a second instance would reject this one's tokens. Set \
                DESKDIBS_JWT_SECRET to a value of at least {} characters to keep sessions across \
                restarts.""", MINIMUM_KEY_BYTES);
        return new SecretKeySpec(material, HMAC_ALGORITHM);
    }
}
