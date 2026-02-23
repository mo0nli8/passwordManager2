package com.passwordmanager.model;

/** One finding produced by the Security Audit screen. */
public class AuditResult {

    public enum Issue {
        WEAK_PASSWORD,
        REUSED_PASSWORD,
        OLD_PASSWORD,
        MISSING_URL,
        MISSING_TOTP
    }

    private final long   entryId;
    private final String entryTitle;
    private final Issue  issue;

    public AuditResult(long entryId, String entryTitle, Issue issue) {
        this.entryId    = entryId;
        this.entryTitle = entryTitle;
        this.issue      = issue;
    }

    public long   getEntryId()    { return entryId; }
    public String getEntryTitle() { return entryTitle; }
    public Issue  getIssue()      { return issue; }

    public String getIssueLabel() {
        return switch (issue) {
            case WEAK_PASSWORD   -> "Weak password";
            case REUSED_PASSWORD -> "Reused password";
            case OLD_PASSWORD    -> "Not updated in 90+ days";
            case MISSING_URL     -> "Missing URL";
            case MISSING_TOTP    -> "No TOTP noted";
        };
    }
}
