package com.passwordmanager.ui.controller;

import com.passwordmanager.crypto.TotpUtil;
import com.passwordmanager.service.*;
import com.passwordmanager.ui.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * First-run setup wizard – three steps shown in a single window:
 *   Step 1 – Create master password
 *   Step 2 – Enroll Google Authenticator (scan QR code)
 *   Step 3 – Save backup codes
 */
public class SetupWizardController {

    // Step 1
    @FXML private VBox       step1Pane;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmField;
    @FXML private ProgressBar  strengthBar;
    @FXML private Label        strengthLabel;
    @FXML private Label        step1Error;

    // Step 2
    @FXML private VBox       step2Pane;
    @FXML private ImageView  qrCodeImage;
    @FXML private Label      secretLabel;
    @FXML private TextField  totpVerifyField;
    @FXML private Label      step2Error;

    // Step 3
    @FXML private VBox       step3Pane;
    @FXML private TextArea   codesArea;
    @FXML private CheckBox   savedCheckBox;
    @FXML private Label      step3Error;

    private final AuthService     auth    = AppContext.getInstance().getAuthService();
    private final PasswordGenerator gen   = AppContext.getInstance().getPasswordGenerator();

    private String totpSecret;
    private List<String> backupCodes;

    @FXML
    public void initialize() {
        showStep(1);
        passwordField.textProperty().addListener((obs, o, n) -> updateStrength(n));
    }

    // ── Step 1 ────────────────────────────────────────────────────────────────

    private void updateStrength(String pw) {
        int score = gen.strength(pw);
        strengthBar.setProgress(score / 4.0);
        String[] labels = {"", "Weak", "Fair", "Good", "Strong"};
        String[] styles = {"", "-weak", "-fair", "-good", "-strong"};
        strengthLabel.setText(pw.isEmpty() ? "" : labels[score]);
        strengthBar.getStyleClass().removeIf(s -> s.startsWith("strength"));
        if (!pw.isEmpty()) strengthBar.getStyleClass().add("strength" + styles[score]);
    }

    @FXML
    private void handleNext1() {
        step1Error.setText("");
        String pw      = passwordField.getText();
        String confirm = confirmField.getText();

        if (pw.length() < 8) {
            step1Error.setText("Password must be at least 8 characters."); return;
        }
        if (!pw.equals(confirm)) {
            step1Error.setText("Passwords do not match."); return;
        }
        try {
            totpSecret = auth.setupVault(pw.toCharArray());
            loadQrCode();
            showStep(2);
        } catch (Exception e) {
            step1Error.setText("Setup failed: " + e.getMessage());
        }
    }

    // ── Step 2 ────────────────────────────────────────────────────────────────

    private void loadQrCode() {
        try {
            byte[] png = TotpUtil.generateQrCodePng("Password Manager", totpSecret);
            qrCodeImage.setImage(new Image(new ByteArrayInputStream(png)));
            secretLabel.setText("Manual key: " + totpSecret);
        } catch (Exception e) {
            secretLabel.setText("Could not generate QR code: " + e.getMessage());
        }
    }

    @FXML
    private void handleVerifyTotp() {
        step2Error.setText("");
        String code = totpVerifyField.getText().trim();
        if (code.length() != 6) {
            step2Error.setText("Enter the 6-digit code from your authenticator app."); return;
        }
        try {
            if (!auth.verifyTotp(code)) {
                step2Error.setText("Incorrect code. Make sure your phone clock is accurate."); return;
            }
            // Generate backup codes
            backupCodes = auth.generateBackupCodes(8);
            auth.storeBackupCodes(backupCodes);
            codesArea.setText(String.join("\n", backupCodes));
            showStep(3);
        } catch (Exception e) {
            step2Error.setText("Error: " + e.getMessage());
        }
    }

    // ── Step 3 ────────────────────────────────────────────────────────────────

    @FXML
    private void handleFinish() {
        step3Error.setText("");
        if (!savedCheckBox.isSelected()) {
            step3Error.setText("Please confirm you have saved your backup codes."); return;
        }
        SceneManager.showMainVault();
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void showStep(int step) {
        step1Pane.setVisible(step == 1); step1Pane.setManaged(step == 1);
        step2Pane.setVisible(step == 2); step2Pane.setManaged(step == 2);
        step3Pane.setVisible(step == 3); step3Pane.setManaged(step == 3);
    }
}
