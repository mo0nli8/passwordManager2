package com.passwordmanager.service;

/**
 * Application-level service locator.
 * Controllers and other classes obtain service instances from here
 * instead of constructing them manually.
 */
public class AppContext {

    private static AppContext instance;

    private final AuthService         authService;
    private final VaultService        vaultService;
    private final AuditService        auditService;
    private final PasswordGenerator   passwordGenerator;
    private final ClipboardManager    clipboardManager;
    private final ImportExportService importExportService;

    private AppContext() {
        authService         = new AuthService();
        vaultService        = new VaultService();
        auditService        = new AuditService();
        passwordGenerator   = new PasswordGenerator();
        clipboardManager    = new ClipboardManager();
        importExportService = new ImportExportService(vaultService);
    }

    public static AppContext getInstance() {
        if (instance == null) instance = new AppContext();
        return instance;
    }

    public AuthService         getAuthService()         { return authService; }
    public VaultService        getVaultService()        { return vaultService; }
    public AuditService        getAuditService()        { return auditService; }
    public PasswordGenerator   getPasswordGenerator()   { return passwordGenerator; }
    public ClipboardManager    getClipboardManager()    { return clipboardManager; }
    public ImportExportService getImportExportService() { return importExportService; }
}
