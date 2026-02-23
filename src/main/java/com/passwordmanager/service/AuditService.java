package com.passwordmanager.service;

import com.passwordmanager.dao.*;
import com.passwordmanager.model.*;

import javax.crypto.SecretKey;
import java.util.*;

/**
 * Scans all LOGIN entries and produces a list of security findings.
 *
 * Checks performed:
 *   - WEAK_PASSWORD   : strength score < 2
 *   - REUSED_PASSWORD : same plaintext password used by ≥ 2 entries
 *   - OLD_PASSWORD    : updatedAt > 90 days ago
 *   - MISSING_URL     : url field is blank
 *   - MISSING_TOTP    : totp field is blank
 */
public class AuditService {

    private static final long NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000;

    private final EntryDAO         entryDAO    = new EntryDAO();
    private final FieldDAO         fieldDAO    = new FieldDAO();
    private final PasswordGenerator generator  = new PasswordGenerator();

    public List<AuditResult> run(SecretKey key) throws Exception {
        List<Entry> logins = entryDAO.findAllLogins();
        List<AuditResult> results = new ArrayList<>();
        Map<String, List<String>> passwordToTitles = new HashMap<>();

        for (Entry e : logins) {
            Map<String, String> fields = fieldDAO.getFields(e.getId(), key);
            String password = fields.getOrDefault("password", "");
            String url      = fields.getOrDefault("url", "");
            String totp     = fields.getOrDefault("totp", "");

            // Weak password
            if (!password.isBlank() && generator.strength(password) < 2) {
                results.add(new AuditResult(e.getId(), e.getTitle(), AuditResult.Issue.WEAK_PASSWORD));
            }

            // Old password
            long age = System.currentTimeMillis() - e.getUpdatedAt();
            if (age > NINETY_DAYS_MS) {
                results.add(new AuditResult(e.getId(), e.getTitle(), AuditResult.Issue.OLD_PASSWORD));
            }

            // Missing URL
            if (url.isBlank()) {
                results.add(new AuditResult(e.getId(), e.getTitle(), AuditResult.Issue.MISSING_URL));
            }

            // Missing TOTP
            if (totp.isBlank()) {
                results.add(new AuditResult(e.getId(), e.getTitle(), AuditResult.Issue.MISSING_TOTP));
            }

            // Track for reuse check
            if (!password.isBlank()) {
                passwordToTitles.computeIfAbsent(password, k -> new ArrayList<>()).add(e.getTitle());
            }
        }

        // Reused passwords
        Set<String> flaggedTitles = new HashSet<>();
        for (Map.Entry<String, List<String>> pw : passwordToTitles.entrySet()) {
            if (pw.getValue().size() >= 2) {
                for (String title : pw.getValue()) {
                    if (flaggedTitles.add(title)) {
                        // Find the corresponding entry id for this title
                        logins.stream()
                              .filter(e -> e.getTitle().equals(title))
                              .findFirst()
                              .ifPresent(e -> results.add(new AuditResult(
                                      e.getId(), e.getTitle(), AuditResult.Issue.REUSED_PASSWORD)));
                    }
                }
            }
        }

        return results;
    }
}
