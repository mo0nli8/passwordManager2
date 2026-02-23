package com.passwordmanager.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Central scene switcher. Holds a reference to the primary Stage and
 * loads FXML layouts on demand.
 */
public class SceneManager {

    private static Stage primaryStage;

    private SceneManager() {}

    public static void init(Stage stage) {
        primaryStage = stage;
        stage.setTitle("Password Manager");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
    }

    public static Stage getStage() { return primaryStage; }

    // ── Scene shortcuts ───────────────────────────────────────────────────────

    public static void showSetupWizard()   { show("setup_wizard",  900, 600); }
    public static void showUnlockStep1()   { show("unlock_step1",  440, 320); }
    public static void showUnlockStep2()   { show("unlock_step2",  440, 320); }
    public static void showMainVault()     { show("main_vault",    1100, 680); }
    public static void showSettings()      { show("settings",      620, 500); }
    public static void showAudit()         { show("audit",         700, 520); }

    // ── Core loader ───────────────────────────────────────────────────────────

    private static void show(String fxmlName, double width, double height) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource("/com/passwordmanager/fxml/" + fxmlName + ".fxml"));
            Parent root = loader.load();
            applyTheme(root);

            Scene scene = new Scene(root, width, height);
            primaryStage.setScene(scene);
            primaryStage.centerOnScreen();
            primaryStage.show();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load FXML: " + fxmlName, e);
        }
    }

    /**
     * Applies the active theme stylesheet to a scene root.
     * Theme is detected from OS (macOS) or falls back to light.
     */
    public static void applyTheme(Parent root) {
        String theme = detectTheme();
        String css   = SceneManager.class.getResource(
                "/com/passwordmanager/css/" + theme + ".css").toExternalForm();
        root.getStylesheets().setAll(css);
    }

    private static String detectTheme() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            try {
                Process p = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes()).trim();
                if ("Dark".equalsIgnoreCase(out)) return "dark";
            } catch (Exception ignored) {}
        }
        return "light";
    }
}
