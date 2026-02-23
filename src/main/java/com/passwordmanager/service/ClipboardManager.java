package com.passwordmanager.service;

import javafx.application.Platform;
import javafx.scene.input.*;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Copies a value to the system clipboard and schedules an automatic clear.
 *
 * Usage:
 *   ClipboardManager.copy("secret", 30, remaining -> statusLabel.setText(remaining + "s"));
 */
public class ClipboardManager {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "clipboard-clear");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> pendingClear;
    private String             copiedValue;

    /**
     * Copies {@code value} to the clipboard and schedules a clear after {@code delaySeconds}.
     *
     * @param onTick called each second with the seconds remaining (on FX thread); pass null to skip
     */
    public void copy(String value, int delaySeconds, Consumer<Integer> onTick) {
        cancelPending();
        copiedValue = value;

        Platform.runLater(() -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(value);
            Clipboard.getSystemClipboard().setContent(content);
        });

        int[] remaining = {delaySeconds};
        pendingClear = scheduler.scheduleAtFixedRate(() -> {
            remaining[0]--;
            if (onTick != null) Platform.runLater(() -> onTick.accept(remaining[0]));
            if (remaining[0] <= 0) {
                clearNow();
                cancelPending();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /** Clears the clipboard immediately and cancels any countdown. */
    public void clearNow() {
        Platform.runLater(() -> {
            Clipboard cb = Clipboard.getSystemClipboard();
            // Only clear if we put it there
            if (copiedValue != null && copiedValue.equals(cb.getString())) {
                cb.setContent(new ClipboardContent()); // empty content clears it
            }
            copiedValue = null;
        });
        cancelPending();
    }

    private void cancelPending() {
        if (pendingClear != null && !pendingClear.isDone()) {
            pendingClear.cancel(false);
        }
    }
}
