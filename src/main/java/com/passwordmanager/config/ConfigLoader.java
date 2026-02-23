package com.passwordmanager.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Properties;

/**
 * Loads config.properties from the file system.
 * Search order:
 *   1. ./config.properties  (working directory – useful when running from IDE)
 *   2. ~/.passwordmanager/config.properties
 * If neither exists, the bundled default from the classpath is copied to option 2.
 */
public class ConfigLoader {

    private static final Properties PROPS = new Properties();
    private static boolean initialized = false;

    private ConfigLoader() {}

    public static void init() throws IOException {
        Path configPath = resolveConfigPath();
        try (InputStream in = Files.newInputStream(configPath)) {
            PROPS.load(in);
        }
        initialized = true;
    }

    public static String get(String key) {
        ensureInit();
        return PROPS.getProperty(key);
    }

    public static String get(String key, String defaultValue) {
        ensureInit();
        return PROPS.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        String val = get(key);
        if (val == null) return defaultValue;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Path resolveConfigPath() throws IOException {
        // 1. Working directory
        Path local = Path.of("config.properties");
        if (Files.exists(local)) return local;

        // 2. User home
        Path userConfig = Path.of(System.getProperty("user.home"), ".passwordmanager", "config.properties");
        if (!Files.exists(userConfig)) {
            Files.createDirectories(userConfig.getParent());
            try (InputStream bundled = ConfigLoader.class.getResourceAsStream("/config.properties")) {
                if (bundled == null) throw new IOException("Bundled config.properties not found in classpath");
                Files.copy(bundled, userConfig);
            }
        }
        return userConfig;
    }

    private static void ensureInit() {
        if (!initialized) throw new IllegalStateException("ConfigLoader not initialized – call ConfigLoader.init() first");
    }
}
