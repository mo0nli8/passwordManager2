package com.passwordmanager.ui.controller;

import com.passwordmanager.service.*;
import com.passwordmanager.ui.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class UnlockStep1Controller {

    @FXML private PasswordField masterPasswordField;
    @FXML private Label         errorLabel;
    @FXML private Button        unlockButton;

    private final AuthService auth = AppContext.getInstance().getAuthService();

    @FXML
    private void handleUnlock() {
        errorLabel.setText("");
        String pw = masterPasswordField.getText();
        if (pw.isBlank()) { errorLabel.setText("Enter your master password."); return; }

        unlockButton.setDisable(true);
        try {
            if (auth.isLockedOut()) {
                long wait = (auth.lockedUntilMs() - System.currentTimeMillis()) / 1000;
                errorLabel.setText("Too many failed attempts. Wait " + wait + " seconds.");
                return;
            }
            boolean ok = auth.verifyMasterPassword(pw.toCharArray());
            masterPasswordField.clear();
            if (ok) {
                SceneManager.showUnlockStep2();
            } else {
                errorLabel.setText("Incorrect master password.");
            }
        } catch (Exception e) {
            errorLabel.setText("Error: " + e.getMessage());
        } finally {
            unlockButton.setDisable(false);
        }
    }
}
