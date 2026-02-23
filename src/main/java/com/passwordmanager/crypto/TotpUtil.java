package com.passwordmanager.crypto;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;

/**
 * Thin wrapper around the samstevens TOTP library.
 *
 * Handles:
 *   - TOTP secret generation (Base32)
 *   - QR-code image bytes (PNG) for Google Authenticator enrollment
 *   - 6-digit code verification (RFC 6238, 30-second window ±1)
 */
public final class TotpUtil {

    private static final String ISSUER = "Password Manager";

    private TotpUtil() {}

    /** Generate a new Base32 TOTP secret (to be stored encrypted in the DB). */
    public static String generateSecret() {
        return new DefaultSecretGenerator().generate();
    }

    /**
     * Produces a PNG QR-code image the user scans with Google Authenticator.
     *
     * @param accountLabel display name shown in the authenticator app
     * @param secret       Base32 TOTP secret
     * @return raw PNG bytes
     */
    public static byte[] generateQrCodePng(String accountLabel, String secret) throws Exception {
        QrData data = new QrData.Builder()
                .label(accountLabel)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        QrGenerator generator = new ZxingPngQrGenerator();
        return generator.generate(data);
    }

    /**
     * Verifies a 6-digit TOTP code against a stored secret.
     * Accepts codes from the previous/current/next 30-second window.
     */
    public static boolean verify(String secret, String code) {
        CodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(),
                new SystemTimeProvider()
        );
        return verifier.isValidCode(secret, code);
    }
}
