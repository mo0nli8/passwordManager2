# Product Requirements Document — Java Desktop Password Manager

> **Last updated:** 2026-02-23
> **Status:** Revised after requirements interview

---

## 1. Overview

A secure, offline-first desktop password manager built in Java. The user stores and manages credentials locally, protected by a master password and Google Authenticator (TOTP) as a second factor. All sensitive vault data is encrypted at rest in a MySQL database and never leaves the machine.

**Primary user:** Personal tool for a single developer user.
**Platform:** Cross-platform (macOS, Windows, Linux).

---

## 2. Goals

- Provide a secure local vault for passwords, secure notes, credit cards, and identity documents
- Encrypt all sensitive data with AES-256-GCM; never write plaintext credentials to disk
- Require two-factor authentication (master password + TOTP) to unlock the vault
- Offer a clean 3-panel JavaFX desktop UI with OS-native dark/light theming
- Make credential access fast with real-time search, clipboard copy, and system tray access

---

## 3. Non-Goals (v1)

- Cloud sync or remote backup
- Browser extension / autofill
- Mobile companion app
- Global keyboard shortcuts
- Native installer packaging (run from IDE or command line is sufficient for v1)

---

## 4. Target Users

- Single developer user (personal tool)
- Privacy-conscious individuals who prefer a fully local solution

---

## 5. Authentication

### 5.1 First-Run Setup Wizard
1. User creates a **master password** (with strength indicator)
2. App generates a TOTP secret and displays a **QR code** to scan with Google Authenticator
3. User scans QR code and confirms with a live 6-digit TOTP code
4. App generates **8 one-time backup codes** (displayed once, user must save/print them)
5. Vault and MySQL schema are initialized

### 5.2 Login Flow (Two Steps)
1. **Step 1 — Master Password screen:** User enters master password. The key is derived via PBKDF2WithHmacSHA256 (≥100,000 iterations, random 16-byte salt). On success, proceed to step 2.
2. **Step 2 — TOTP screen:** User enters the 6-digit code from Google Authenticator (or a backup code). On success, vault is unlocked.

### 5.3 Rate-Limiting
- After 5 consecutive failed master password attempts: exponential back-off (5 s, 10 s, 20 s…)
- After 5 consecutive failed TOTP attempts: same back-off, no permanent lockout or vault wipe

### 5.4 Auto-Lock
- Vault auto-locks after a configurable idle timeout (default: 5 minutes)
- Minimizing to system tray also immediately locks the vault

### 5.5 Backup Codes
- 8 single-use alphanumeric codes generated at setup
- Each code can substitute for the TOTP step once; used codes are invalidated
- User can regenerate backup codes from Settings (requires both master password + TOTP to confirm)

---

## 6. Entry Types

The vault supports four entry types, each with a distinct schema:

### 6.1 Login
| Field | Encrypted | Required |
|---|---|---|
| Title | No | Yes |
| Username | Yes | No |
| Password | Yes | Yes |
| URL | Yes | No |
| TOTP Secret (for the site) | Yes | No |
| Notes | Yes | No |

### 6.2 Secure Note
| Field | Encrypted | Required |
|---|---|---|
| Title | No | Yes |
| Body | Yes | Yes |

### 6.3 Credit Card
| Field | Encrypted | Required |
|---|---|---|
| Title | No | Yes |
| Cardholder Name | Yes | Yes |
| Card Number | Yes | Yes |
| Expiry (MM/YY) | Yes | Yes |
| CVV | Yes | Yes |
| PIN | Yes | No |
| Notes | Yes | No |

### 6.4 Identity
| Field | Encrypted | Required |
|---|---|---|
| Title | No | Yes |
| Full Name | Yes | Yes |
| Date of Birth | Yes | No |
| Passport Number | Yes | No |
| National ID Number | Yes | No |
| Notes | Yes | No |

**Note on title encryption:** Entry titles (e.g. "Gmail", "Bank of America") are stored **unencrypted** in the database to support fast indexed search and sorting. All other sensitive fields are encrypted individually with AES-256-GCM.

---

## 7. Core Features

### 7.1 Credential CRUD
- Add, view, edit, delete entries of any type
- Confirmation dialog before delete
- Reveal/hide toggle for password, CVV, PIN fields
- Created/updated timestamps tracked automatically

### 7.2 Categories + Tags
- Each entry belongs to **one category** (user-defined, e.g. "Work", "Banking", "Social")
- Entries can also have **zero or more optional tags** (many-to-many) for cross-cutting labels
- Sidebar shows all categories; tag filter available in the search bar

### 7.3 Favorites
- Star any entry to add it to the "Favorites" view in the sidebar

### 7.4 Real-Time Search & Filter
- Search across: title, username, URL (URL decrypted in-memory after unlock)
- Filter by category and/or tag
- Sort by: title (A–Z), last modified, date created

### 7.5 Password Generator
- Configurable length: 8–128 characters
- Toggle character sets: uppercase, lowercase, digits, symbols
- One-click copy of generated password
- Accessible from the toolbar and inline within the Add/Edit entry dialog

### 7.6 Clipboard Management
- One-click copy of any sensitive field (username, password, card number, etc.)
- Clipboard automatically cleared after **30 seconds** (configurable in Settings)
- Status bar shows a live countdown while the clipboard is occupied

### 7.7 Password History
- When a **Login** entry's password is changed, the previous password is saved to an encrypted history log
- The last **5 versions** are retained per entry (older ones are deleted)
- History is visible in the entry detail panel under a collapsible "History" section

### 7.8 Security Audit Screen
A dedicated screen that scans all Login entries and reports:
| Check | Rule |
|---|---|
| Weak passwords | Password strength score below a defined threshold |
| Reused passwords | Two or more entries share the same password |
| Old passwords | Password not updated in 90+ days |
| Missing URL | Login entry has no URL filled in |
| Missing TOTP noted | Login entry has no site TOTP secret stored |

Results shown as a sortable list with one-click navigation to each flagged entry.

### 7.9 Import / Export
| Format | Direction | Notes |
|---|---|---|
| Own encrypted JSON | Import + Export | Protected with master password |
| Bitwarden JSON export | Import only | Standard Bitwarden export format |
| KeePass CSV | Import only | Standard KeePass CSV columns |

### 7.10 System Tray
- Minimizing the window sends the app to the system tray and **immediately locks** the vault
- Tray icon right-click menu: "Open" (triggers unlock flow), "Quit"

---

## 8. Security Requirements

| Requirement | Detail |
|---|---|
| Encryption algorithm | AES-256-GCM |
| Key derivation | PBKDF2WithHmacSHA256, ≥100,000 iterations |
| Salt | 16 bytes, randomly generated once per vault, stored in `vault_meta` |
| IV / Nonce | 12 bytes, randomly generated per encryption operation, prepended to ciphertext |
| Memory handling | Sensitive strings stored as `char[]`, zeroed after use |
| No plaintext on disk | Plaintext credentials are never written to disk or log files |
| TOTP secret storage | TOTP secret is stored AES-256-GCM encrypted in the database |
| Backup code storage | Backup codes stored as bcrypt hashes, not plaintext |
| MySQL credentials | Stored in plaintext `config.properties` (localhost access only); user is responsible for OS-level file permissions |

---

## 9. Technology Stack

| Layer | Technology | Rationale |
|---|---|---|
| Language | Java 17 (LTS) | Long-term support, wide tooling |
| UI Framework | JavaFX 21 | Modern desktop UI, CSS theming, FXML layout |
| Build Tool | Maven | Standard dependency management |
| Database | MySQL 8.x | Learning/portfolio exercise with a real relational DB server |
| DB Access | Plain JDBC + DAO pattern | Full SQL control, no ORM abstraction needed |
| DB Connection Pool | HikariCP | Lightweight JDBC connection pooling for MySQL |
| Crypto | `javax.crypto` (JDK built-in) | AES-GCM and PBKDF2 available without extra dependencies |
| TOTP | `dev.samstevens.totp` library | RFC 6238-compliant TOTP + QR code generation |
| Testing | JUnit 5 + Mockito | Unit and integration testing |
| Packaging | Run from IDE / `mvn javafx:run` | Native installer deferred to post-v1 |

---

## 10. Database

### 10.1 Connection Configuration (`config.properties`)
```properties
db.host=localhost
db.port=3306
db.name=password_manager
db.user=pm_user
db.password=changeme
```
Loaded at startup. A setup screen is shown on first launch if the connection fails.

### 10.2 Schema

```sql
-- Vault-level metadata
CREATE TABLE vault_meta (
    key_name  VARCHAR(64)  PRIMARY KEY,
    value     TEXT         NOT NULL
);
-- Stores: kdf_salt (hex), kdf_iterations, app_version, totp_secret (encrypted)

-- Entry types lookup
CREATE TABLE entry_types (
    id    INT          PRIMARY KEY AUTO_INCREMENT,
    name  VARCHAR(32)  NOT NULL UNIQUE  -- 'login', 'note', 'card', 'identity'
);

-- Categories (user-defined)
CREATE TABLE categories (
    id    INT          PRIMARY KEY AUTO_INCREMENT,
    name  VARCHAR(64)  NOT NULL UNIQUE
);

-- Tags (user-defined)
CREATE TABLE tags (
    id    INT          PRIMARY KEY AUTO_INCREMENT,
    name  VARCHAR(64)  NOT NULL UNIQUE
);

-- Main entries table
CREATE TABLE entries (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    type_id       INT          NOT NULL REFERENCES entry_types(id),
    title         VARCHAR(255) NOT NULL,           -- plaintext (intentional)
    category_id   INT          REFERENCES categories(id),
    favorite      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at    BIGINT       NOT NULL,
    updated_at    BIGINT       NOT NULL
);

-- Encrypted field store (flexible per-type fields)
CREATE TABLE entry_fields (
    id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
    entry_id   BIGINT       NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
    field_key  VARCHAR(64)  NOT NULL,   -- e.g. 'username', 'password', 'card_number'
    value_enc  BLOB         NOT NULL,   -- AES-GCM ciphertext (IV prepended)
    UNIQUE (entry_id, field_key)
);

-- Password history (Login entries only)
CREATE TABLE password_history (
    id          BIGINT   PRIMARY KEY AUTO_INCREMENT,
    entry_id    BIGINT   NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
    value_enc   BLOB     NOT NULL,
    changed_at  BIGINT   NOT NULL
);

-- Entry ↔ Tag many-to-many
CREATE TABLE entry_tags (
    entry_id  BIGINT  NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
    tag_id    INT     NOT NULL REFERENCES tags(id)    ON DELETE CASCADE,
    PRIMARY KEY (entry_id, tag_id)
);

-- Backup codes (bcrypt hashed)
CREATE TABLE backup_codes (
    id           INT          PRIMARY KEY AUTO_INCREMENT,
    code_hash    VARCHAR(60)  NOT NULL,   -- bcrypt hash
    used         TINYINT(1)   NOT NULL DEFAULT 0
);
```

---

## 11. Architecture

```
┌──────────────────────────────────────────────────────┐
│                    JavaFX UI Layer                    │
│   FXML Views + Controllers (UnlockCtrl, VaultCtrl,   │
│   EntryDetailCtrl, AuditCtrl, SettingsCtrl, …)        │
└───────────────────────────┬──────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────┐
│                   Service Layer                       │
│  AuthService │ VaultService │ AuditService │          │
│  PasswordGenerator │ ClipboardManager │ ImportExport  │
└───────────────────────────┬──────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────┐
│                  Encryption Layer                     │
│  CryptoUtil (AES-256-GCM, PBKDF2, IV generation)     │
│  TotpUtil   (TOTP verify, QR code generation)         │
└───────────────────────────┬──────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────┐
│                 Persistence Layer                     │
│  MySQL ←→ HikariCP ←→ DAO classes ←→ Model POJOs     │
│  (EntryDAO, FieldDAO, HistoryDAO, TagDAO, MetaDAO)    │
└──────────────────────────────────────────────────────┘
```

### Key Classes

| Class | Responsibility |
|---|---|
| `App` | JavaFX entry point, stage and scene management |
| `AuthService` | PBKDF2 key derivation, TOTP verify, backup code verify, session state |
| `VaultService` | CRUD for all entry types, delegates to DAOs |
| `CryptoUtil` | AES-GCM encrypt/decrypt, PBKDF2, secure random IV generation |
| `TotpUtil` | TOTP secret generation, QR code URL, 6-digit code verification |
| `PasswordGenerator` | Configurable random password generation |
| `AuditService` | Scans entries for weak/reused/old passwords and missing fields |
| `ClipboardManager` | Copy-to-clipboard + scheduled clear with countdown |
| `ImportExportService` | Read/write own JSON, Bitwarden JSON, KeePass CSV |
| `EntryDAO` | CRUD queries on `entries` table |
| `FieldDAO` | Read/write encrypted fields in `entry_fields` |
| `HistoryDAO` | Manage password history, enforce 5-version cap |
| `TagDAO` | Tag CRUD and entry-tag associations |
| `MetaDAO` | Read/write `vault_meta` (salt, TOTP secret, config) |
| `DatabaseManager` | HikariCP pool init, schema migration on startup |

---

## 12. UI Screens

### 12.1 Unlock Screen — Step 1 (Master Password)
- Single password field, "Unlock" button
- Error message and back-off delay shown on failure

### 12.2 Unlock Screen — Step 2 (TOTP)
- 6-digit code input (auto-submits when 6 digits entered)
- "Use a backup code instead" link

### 12.3 First-Run Setup Wizard
1. Create master password (with strength meter)
2. Scan QR code with Google Authenticator, confirm with live code
3. Download / copy backup codes screen (cannot proceed without acknowledging)

### 12.4 Main Vault Screen (3-Panel Layout)
```
┌──────────────┬──────────────────┬──────────────────────────┐
│   Sidebar    │   Entry List     │   Entry Detail           │
│   ─────────  │   ─────────────  │   ──────────────────     │
│   All        │   [🔑] Gmail  ★  │   Title:  Gmail          │
│   Favorites  │   [🔑] Netflix   │   Type:   Login          │
│   ─────────  │   [💳] Visa    ◀ │   User:   john@...  [⎘]  │
│   Work       │   [📝] SSH Key   │   Pass:   ••••••   [👁][⎘]│
│   Banking    │   ...            │   URL:    gmail.com [⎘]  │
│   Social     │                  │   ──────────────────     │
│   + New Cat  │                  │   History ▼              │
│   ─────────  │                  │   Tags: [work] [email]   │
│   Tags       │                  │   [Edit]   [Delete]      │
│   #work      │                  │                          │
│   #email     │                  │                          │
└──────────────┴──────────────────┴──────────────────────────┘
[ 🔍 Search...                    ]  [ + Add ]  [ 🛡 Audit ]
```

### 12.5 Add / Edit Entry Dialog
- Dropdown to select entry type (Login / Note / Card / Identity)
- Fields change dynamically based on type selected
- Inline password generator button next to password field
- Category picker + tag input (comma-separated)
- Save / Cancel

### 12.6 Security Audit Screen
- Table of flagged entries with check type, entry title, and a "Go to entry" link
- Summary counts per check category at the top

### 12.7 Settings Screen (Tabbed)
| Tab | Contents |
|---|---|
| Security | Change master password, regenerate TOTP / backup codes, auto-lock timeout |
| Appearance | Clipboard clear delay (default 30 s) |
| Database | View/edit DB connection config (requires re-launch) |
| Advanced | Import, Export |

---

## 13. Implementation Milestones

### Milestone 1 — Foundation
- [ ] Maven project setup: JavaFX 21, MySQL connector, HikariCP, TOTP library
- [ ] `DatabaseManager`: HikariCP pool init, full schema creation on first run
- [ ] `CryptoUtil`: PBKDF2 key derivation + AES-GCM encrypt/decrypt
- [ ] `TotpUtil`: secret generation, QR code URL, code verification
- [ ] `AuthService`: session management (holds derived key in memory)
- [ ] `config.properties` loader and DB connection error screen

### Milestone 2 — Authentication UI
- [ ] First-run setup wizard (master password + TOTP enroll + backup codes)
- [ ] Unlock Step 1 screen (master password)
- [ ] Unlock Step 2 screen (TOTP / backup code)
- [ ] Rate-limiting and back-off UI feedback

### Milestone 3 — Core Vault
- [ ] `EntryDAO`, `FieldDAO`, `MetaDAO`, `TagDAO` — all CRUD operations
- [ ] `VaultService` — business logic for all four entry types
- [ ] 3-panel main vault screen
- [ ] Add / Edit / Delete entry dialogs for all four types
- [ ] Category sidebar + Favorites

### Milestone 4 — Usability
- [ ] Real-time search (title + in-memory decrypted URL/username)
- [ ] Category filter + tag filter
- [ ] Password generator UI dialog
- [ ] Clipboard copy + 30-second auto-clear with countdown
- [ ] Password history — `HistoryDAO`, history UI in detail panel

### Milestone 5 — Polish & Security
- [ ] Auto-lock idle timer
- [ ] System tray integration (minimize → lock)
- [ ] OS-native theme detection (light / dark via JavaFX CSS)
- [ ] Settings screen (all tabs)
- [ ] Security Audit screen + `AuditService`

### Milestone 6 — Import / Export
- [ ] Export to own encrypted JSON format
- [ ] Import from own encrypted JSON
- [ ] Import from Bitwarden JSON export
- [ ] Import from KeePass CSV

---

## 14. Out of Scope (v1)

- Cloud sync or remote backup
- Browser extension / autofill
- Global keyboard shortcuts
- Native installer packaging (.dmg / .exe)
- Password breach detection (Have I Been Pwned)
- Team or shared vaults
- Mobile companion app

---

## 15. Success Criteria

- All sensitive credential fields encrypted at rest with AES-256-GCM
- Vault requires both master password + TOTP to unlock (2FA enforced)
- Vault unlocks in under 2 seconds on standard hardware after correct credentials
- Zero plaintext credentials written to disk at any time
- Security Audit correctly identifies weak, reused, and old passwords
- Import from Bitwarden JSON and KeePass CSV works without data loss
