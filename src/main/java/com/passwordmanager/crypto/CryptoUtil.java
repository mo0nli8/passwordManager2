package com.passwordmanager.crypto;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.Arrays;

/**
 * AES-256-GCM encryption / decryption + PBKDF2 key derivation.
 *
 * Ciphertext format on disk: [ 12-byte IV | ciphertext+GCM-tag ]
 * The GCM tag (16 bytes) is appended by the JCE cipher automatically.
 */
public final class CryptoUtil {

    private static final String ALGORITHM        = "AES";
    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LEN       = 12;   // bytes
    private static final int    GCM_TAG_BITS     = 128;  // bits
    private static final int    KEY_BITS         = 256;
    private static final String KDF_ALGORITHM    = "PBKDF2WithHmacSHA256";

    private CryptoUtil() {}

    // ── Key derivation ────────────────────────────────────────────────────────

    /**
     * Derives a 256-bit AES key from a master password + salt using PBKDF2.
     * The caller is responsible for clearing {@code password} after use.
     */
    public static SecretKey deriveKey(char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } finally {
            spec.clearPassword();
        }
    }

    // ── Encryption ────────────────────────────────────────────────────────────

    /** Encrypts raw bytes. Returns {@code IV || ciphertext+tag}. */
    public static byte[] encrypt(byte[] plaintext, SecretKey key) throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_LEN];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] cipherAndTag = cipher.doFinal(plaintext);

        byte[] result = new byte[iv.length + cipherAndTag.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(cipherAndTag, 0, result, iv.length, cipherAndTag.length);
        return result;
    }

    /** Convenience: encrypt a UTF-8 string. */
    public static byte[] encryptString(String plaintext, SecretKey key) throws GeneralSecurityException {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), key);
    }

    // ── Decryption ────────────────────────────────────────────────────────────

    /** Decrypts {@code IV || ciphertext+tag} back to raw bytes. */
    public static byte[] decrypt(byte[] ivAndCipher, SecretKey key) throws GeneralSecurityException {
        if (ivAndCipher.length <= GCM_IV_LEN) throw new IllegalArgumentException("Ciphertext too short");
        byte[] iv         = Arrays.copyOfRange(ivAndCipher, 0, GCM_IV_LEN);
        byte[] cipherText = Arrays.copyOfRange(ivAndCipher, GCM_IV_LEN, ivAndCipher.length);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(cipherText);
    }

    /** Convenience: decrypt to a UTF-8 string. */
    public static String decryptString(byte[] ivAndCipher, SecretKey key) throws GeneralSecurityException {
        return new String(decrypt(ivAndCipher, key), StandardCharsets.UTF_8);
    }

    // ── Random helpers ────────────────────────────────────────────────────────

    /** Generates a 16-byte random salt suitable for key derivation. */
    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    // ── Hex utilities ─────────────────────────────────────────────────────────

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static byte[] fromHex(String hex) {
        if (hex.length() % 2 != 0) throw new IllegalArgumentException("Odd hex string length");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) ((Character.digit(hex.charAt(i * 2), 16) << 4)
                           |  Character.digit(hex.charAt(i * 2 + 1), 16));
        }
        return out;
    }
}
