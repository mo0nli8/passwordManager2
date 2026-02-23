package com.passwordmanager.service;

import java.security.SecureRandom;

/** Generates cryptographically random passwords with configurable character sets. */
public class PasswordGenerator {

    private static final String UPPER   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER   = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS  = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?";

    private final SecureRandom rng = new SecureRandom();

    public String generate(int length, boolean useUpper, boolean useLower,
                           boolean useDigits, boolean useSymbols) {
        if (length < 1) throw new IllegalArgumentException("Length must be ≥ 1");

        StringBuilder alphabet = new StringBuilder();
        if (useUpper)   alphabet.append(UPPER);
        if (useLower)   alphabet.append(LOWER);
        if (useDigits)  alphabet.append(DIGITS);
        if (useSymbols) alphabet.append(SYMBOLS);
        if (alphabet.isEmpty()) alphabet.append(LOWER); // fallback

        String pool = alphabet.toString();
        StringBuilder pw = new StringBuilder(length);

        // Guarantee at least one character from each selected set
        if (useUpper)   pw.append(UPPER.charAt(rng.nextInt(UPPER.length())));
        if (useLower)   pw.append(LOWER.charAt(rng.nextInt(LOWER.length())));
        if (useDigits)  pw.append(DIGITS.charAt(rng.nextInt(DIGITS.length())));
        if (useSymbols) pw.append(SYMBOLS.charAt(rng.nextInt(SYMBOLS.length())));

        while (pw.length() < length) pw.append(pool.charAt(rng.nextInt(pool.length())));

        // Fisher-Yates shuffle to avoid predictable positions
        char[] arr = pw.toString().toCharArray();
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            char tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
        }
        return new String(arr);
    }

    /** Scores password strength: 0 (very weak) → 4 (very strong). */
    public int strength(String password) {
        if (password == null || password.isEmpty()) return 0;
        int score = 0;
        if (password.length() >= 8)  score++;
        if (password.length() >= 14) score++;
        if (password.chars().anyMatch(Character::isUpperCase)) score++;
        if (password.chars().anyMatch(Character::isDigit))     score++;
        if (password.chars().anyMatch(c -> SYMBOLS.indexOf(c) >= 0)) score++;
        return Math.min(score, 4);
    }
}
