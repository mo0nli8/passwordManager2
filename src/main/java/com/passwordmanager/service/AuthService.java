package com.passwordmanager.service;

import com.passwordmanager.crypto.*;
import com.passwordmanager.dao.*;

import javax.crypto.SecretKey;
import java.util.*;

/**
 * Handles all authentication concerns:
 *   - First-run vault setup (master password + TOTP enrollment + backup codes)
 *   - Two-step unlock (master password → TOTP/backup code)
 *   - Session key management (holds derived AES key in memory)
 *   - Rate-limiting on failed attempts
 */
public class AuthService {

    // ── Meta keys ─────────────────────────────────────────────────────────────
    private static final String KEY_SALT        = "kdf_salt";
    private static final String KEY_ITERATIONS  = "kdf_iterations";
    private static final String KEY_TOTP_SECRET = "totp_secret_enc";
    private static final int    DEFAULT_ITER    = 200_000;

    // ── Rate-limiting ─────────────────────────────────────────────────────────
    private static final int MAX_ATTEMPTS   = 5;
    private static final long BASE_DELAY_MS = 5_000;

    private final MetaDAO       metaDAO      = new MetaDAO();
    private final BackupCodeDAO backupDAO    = new BackupCodeDAO();

    // ── Session state (in-memory only) ────────────────────────────────────────
    private SecretKey sessionKey;
    private int  failedPasswordAttempts = 0;
    private int  failedTotpAttempts     = 0;
    private long lockedUntil            = 0;

    // ── Setup ─────────────────────────────────────────────────────────────────

    /**
     * First-run setup: derives and stores the KDF parameters, then sets up TOTP.
     *
     * @param password master password (cleared after use)
     * @return the TOTP secret (Base32) that the user must scan
     */
    public String setupVault(char[] password) throws Exception {
        byte[] salt       = CryptoUtil.generateSalt();
        String saltHex    = CryptoUtil.toHex(salt);
        SecretKey key     = CryptoUtil.deriveKey(password, salt, DEFAULT_ITER);

        // Generate TOTP secret and encrypt it with the vault key
        String totpSecret  = TotpUtil.generateSecret();
        byte[] encTotp     = CryptoUtil.encryptString(totpSecret, key);
        String encTotpHex  = CryptoUtil.toHex(encTotp);

        metaDAO.set(KEY_SALT,        saltHex);
        metaDAO.set(KEY_ITERATIONS,  String.valueOf(DEFAULT_ITER));
        metaDAO.set(KEY_TOTP_SECRET, encTotpHex);

        // Hold the key in session so the wizard can immediately access the vault
        sessionKey = key;
        Arrays.fill(password, '\0');
        return totpSecret;
    }

    /** Stores bcrypt-hashed backup codes. Call during first-run setup. */
    public void storeBackupCodes(List<String> plainCodes) throws Exception {
        backupDAO.replaceAll(plainCodes);
    }

    /** Generates N random backup codes (plaintext). */
    public List<String> generateBackupCodes(int count) {
        List<String> codes = new ArrayList<>();
        Random rng = new Random();
        for (int i = 0; i < count; i++) {
            codes.add(String.format("%04d-%04d-%04d", rng.nextInt(10000),
                    rng.nextInt(10000), rng.nextInt(10000)));
        }
        return codes;
    }

    // ── Step 1: master password ───────────────────────────────────────────────

    /** @return true if password is correct and rate-limit is not active */
    public boolean verifyMasterPassword(char[] password) throws Exception {
        if (isLockedOut()) return false;

        String saltHex = metaDAO.get(KEY_SALT);
        int    iters   = Integer.parseInt(metaDAO.get(KEY_ITERATIONS));
        byte[] salt    = CryptoUtil.fromHex(saltHex);

        SecretKey candidate = CryptoUtil.deriveKey(password, salt, iters);
        Arrays.fill(password, '\0');

        // Validation: try to decrypt the TOTP secret with the candidate key
        try {
            String encHex   = metaDAO.get(KEY_TOTP_SECRET);
            byte[] encBytes = CryptoUtil.fromHex(encHex);
            CryptoUtil.decryptString(encBytes, candidate); // throws if wrong key
            // Correct password – hold candidate key for step 2
            sessionKey             = candidate;
            failedPasswordAttempts = 0;
            return true;
        } catch (Exception e) {
            sessionKey = null;
            recordPasswordFailure();
            return false;
        }
    }

    // ── Step 2: TOTP ──────────────────────────────────────────────────────────

    /** Verifies the 6-digit TOTP code against the stored secret. */
    public boolean verifyTotp(String code) throws Exception {
        if (sessionKey == null) throw new IllegalStateException("Complete step 1 first");
        if (isLockedOut()) return false;

        String totpSecret = getTotpSecret();
        if (TotpUtil.verify(totpSecret, code)) {
            failedTotpAttempts = 0;
            return true;
        }
        recordTotpFailure();
        return false;
    }

    /** Verifies a one-time backup code. */
    public boolean verifyBackupCode(String code) throws Exception {
        if (sessionKey == null) throw new IllegalStateException("Complete step 1 first");
        return backupDAO.verifyAndConsume(code);
    }

    // ── Session ───────────────────────────────────────────────────────────────

    public boolean isVaultSetup() throws Exception {
        return metaDAO.exists(KEY_SALT);
    }

    public boolean isUnlocked() { return sessionKey != null; }

    public SecretKey getSessionKey() {
        if (!isUnlocked()) throw new IllegalStateException("Vault is locked");
        return sessionKey;
    }

    public void lock() { sessionKey = null; }

    // ── Rate-limiting ─────────────────────────────────────────────────────────

    public boolean isLockedOut() { return System.currentTimeMillis() < lockedUntil; }

    public long lockedUntilMs() { return lockedUntil; }

    private void recordPasswordFailure() {
        failedPasswordAttempts++;
        applyBackoff(failedPasswordAttempts);
    }

    private void recordTotpFailure() {
        failedTotpAttempts++;
        applyBackoff(failedTotpAttempts);
    }

    private void applyBackoff(int attempts) {
        if (attempts >= MAX_ATTEMPTS) {
            long delay = BASE_DELAY_MS * (1L << Math.min(attempts - MAX_ATTEMPTS, 10));
            lockedUntil = System.currentTimeMillis() + delay;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the decrypted TOTP secret using the current session key. */
    private String getTotpSecret() throws Exception {
        String encHex   = metaDAO.get(KEY_TOTP_SECRET);
        byte[] encBytes = CryptoUtil.fromHex(encHex);
        return CryptoUtil.decryptString(encBytes, sessionKey);
    }

    /**
     * Returns the raw TOTP secret for QR-code display during first-run setup.
     * Safe to call only right after {@link #setupVault}.
     */
    public String getTotpSecretForSetup() throws Exception {
        return getTotpSecret();
    }

    /** Changes the master password (re-encrypts the TOTP secret with the new key). */
    public void changeMasterPassword(char[] oldPassword, char[] newPassword) throws Exception {
        if (!verifyMasterPassword(oldPassword)) throw new SecurityException("Incorrect current password");

        byte[] newSalt    = CryptoUtil.generateSalt();
        SecretKey newKey  = CryptoUtil.deriveKey(newPassword, newSalt, DEFAULT_ITER);
        String totpSecret = getTotpSecret();                            // decrypt with old key
        byte[] reencTotp  = CryptoUtil.encryptString(totpSecret, newKey);

        metaDAO.set(KEY_SALT,        CryptoUtil.toHex(newSalt));
        metaDAO.set(KEY_ITERATIONS,  String.valueOf(DEFAULT_ITER));
        metaDAO.set(KEY_TOTP_SECRET, CryptoUtil.toHex(reencTotp));

        sessionKey = newKey;
        Arrays.fill(newPassword, '\0');
    }
}
