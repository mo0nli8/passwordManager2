package com.passwordmanager.ui.controller;

import com.passwordmanager.service.*;
import com.passwordmanager.ui.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class UnlockStep2Controller {

    @FXML private TextField totpField;
    @FXML private Label     errorLabel;
    @FXML private Button    verifyButton;

    // Backup code section (hidden by default)
    @FXML private VBox      backupPane;
    @FXML private TextField backupCodeField;
    @FXML private Label     backupError;

    private final AuthService auth = AppContext.getInstance().getAuthService();

    @FXML
    public void initialize() {
        backupPane.setVisible(false);
        backupPane.setManaged(false);

        // Auto-submit when 6 digits entered
        totpField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.length() == 6) handleVerify();
        });
    }

    @FXML
    private void handleVerify() {
        errorLabel.setText("");
        String code = totpField.getText().trim();
        if (code.length() != 6) { errorLabel.setText("Enter the 6-digit code."); return; }

        verifyButton.setDisable(true);
        try {
            if (auth.verifyTotp(code)) {
                SceneManager.showMainVault();
            } else {
                errorLabel.setText("Incorrect code. Try again.");
                totpField.clear();
            }
        } catch (Exception e) {
            errorLabel.setText("Error: " + e.getMessage());
        } finally {
            verifyButton.setDisable(false);
        }
    }

    @FXML
    private void handleShowBackup() {
        backupPane.setVisible(true);
        backupPane.setManaged(true);
    }

    @FXML
    private void handleVerifyBackup() {
        backupError.setText("");
        String code = backupCodeField.getText().trim();
        if (code.isBlank()) { backupError.setText("Enter a backup code."); return; }
        try {
            if (auth.verifyBackupCode(code)) {
                SceneManager.showMainVault();
            } else {
                backupError.setText("Invalid or already used backup code.");
            }
        } catch (Exception e) {
            backupError.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleBack() {
        auth.lock();
        SceneManager.showUnlockStep1();
    }
}
