package com.passwordmanager;

import com.passwordmanager.config.ConfigLoader;
import com.passwordmanager.db.DatabaseManager;
import com.passwordmanager.service.*;
import com.passwordmanager.ui.SceneManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * JavaFX entry point.
 *
 * Startup flow:
 *   1. Load config.properties
 *   2. Connect to MySQL + run schema migrations
 *   3. If vault not set up → show Setup Wizard
 *      Else → show Unlock Step 1 (master password)
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        SceneManager.init(primaryStage);

        // Initialise config
        try {
            ConfigLoader.init();
        } catch (Exception e) {
            showFatalError("Configuration error", e.getMessage());
            return;
        }

        // Initialise database
        try {
            DatabaseManager.init();
        } catch (Exception e) {
            showFatalError("Database connection failed",
                    "Cannot connect to MySQL.\n\n"
                    + "Please check config.properties and ensure MySQL is running.\n\n"
                    + "Details: " + e.getMessage());
            return;
        }

        // Route to setup or unlock
        try {
            AuthService auth = AppContext.getInstance().getAuthService();
            if (auth.isVaultSetup()) {
                SceneManager.showUnlockStep1();
            } else {
                SceneManager.showSetupWizard();
            }
        } catch (Exception e) {
            showFatalError("Startup error", e.getMessage());
        }
    }

    @Override
    public void stop() {
        DatabaseManager.shutdown();
    }

    // ── Fatal error dialog ────────────────────────────────────────────────────

    private static void showFatalError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Password Manager – Error");
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
            Platform.exit();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
