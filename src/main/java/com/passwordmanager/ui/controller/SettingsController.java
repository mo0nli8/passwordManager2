package com.passwordmanager.ui.controller;

import com.passwordmanager.service.*;
import com.passwordmanager.ui.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class SettingsController {

    // Security tab
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label         changePasswordStatus;

    // Advanced tab
    @FXML private Label importStatus;
    @FXML private Label exportStatus;

    private final AuthService         auth    = AppContext.getInstance().getAuthService();
    private final ImportExportService impExp  = AppContext.getInstance().getImportExportService();
    private final VaultService        vault   = AppContext.getInstance().getVaultService();

    // ── Security tab ──────────────────────────────────────────────────────────

    @FXML
    private void handleChangeMasterPassword() {
        changePasswordStatus.setText("");
        String oldPw  = oldPasswordField.getText();
        String newPw  = newPasswordField.getText();
        String confirm= confirmPasswordField.getText();

        if (newPw.length() < 8) {
            changePasswordStatus.setText("New password must be at least 8 characters."); return;
        }
        if (!newPw.equals(confirm)) {
            changePasswordStatus.setText("New passwords do not match."); return;
        }
        try {
            auth.changeMasterPassword(oldPw.toCharArray(), newPw.toCharArray());
            changePasswordStatus.setText("Password changed successfully.");
            oldPasswordField.clear(); newPasswordField.clear(); confirmPasswordField.clear();
        } catch (SecurityException e) {
            changePasswordStatus.setText("Incorrect current password.");
        } catch (Exception e) {
            changePasswordStatus.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleRegenerateBackupCodes() {
        try {
            List<String> codes = auth.generateBackupCodes(8);
            auth.storeBackupCodes(codes);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("New Backup Codes");
            alert.setContentText(String.join("\n", codes));
            alert.showAndWait();
        } catch (Exception e) {
            showError("Failed to regenerate backup codes: " + e.getMessage());
        }
    }

    // ── Advanced tab ──────────────────────────────────────────────────────────

    @FXML
    private void handleExport() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Vault");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PM Encrypted JSON", "*.pmjson"));
        File dest = fc.showSaveDialog(null);
        if (dest == null) return;

        Dialog<String> pwDialog = new TextInputDialog();
        pwDialog.setTitle("Export Password");
        pwDialog.setHeaderText("Enter your master password to encrypt the export:");
        pwDialog.showAndWait().ifPresent(pw -> {
            try {
                impExp.exportEncrypted(dest.toPath(), auth.getSessionKey(), pw.toCharArray());
                exportStatus.setText("Exported to " + dest.getName());
            } catch (Exception e) {
                exportStatus.setText("Export failed: " + e.getMessage());
            }
        });
    }

    @FXML
    private void handleImportOwn() {
        doImport("PM Encrypted JSON", "*.pmjson", (path, pw) ->
                impExp.importEncrypted(path, pw, auth.getSessionKey()));
    }

    @FXML
    private void handleImportBitwarden() {
        doImport("Bitwarden JSON", "*.json", (path, pw) ->
                impExp.importBitwarden(path, auth.getSessionKey()));
    }

    @FXML
    private void handleImportKeePass() {
        doImport("KeePass CSV", "*.csv", (path, pw) ->
                impExp.importKeePassCsv(path, auth.getSessionKey()));
    }

    private void doImport(String desc, String ext, ImportFn fn) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import " + desc);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, ext));
        File src = fc.showOpenDialog(null);
        if (src == null) return;

        try {
            char[] pw = {};
            if (ext.endsWith("pmjson")) {
                TextInputDialog d = new TextInputDialog();
                d.setTitle("Import Password");
                d.setHeaderText("Enter the master password that was used when exporting:");
                var res = d.showAndWait();
                if (res.isEmpty()) return;
                pw = res.get().toCharArray();
            }
            int count = fn.run(src.toPath(), pw);
            importStatus.setText("Imported " + count + " entries from " + src.getName());
        } catch (Exception e) {
            importStatus.setText("Import failed: " + e.getMessage());
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @FXML private void handleBack() { SceneManager.showMainVault(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }

    @FunctionalInterface
    private interface ImportFn {
        int run(Path path, char[] password) throws Exception;
    }
}
