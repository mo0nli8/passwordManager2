package com.passwordmanager.ui.controller;

import com.passwordmanager.config.ConfigLoader;
import com.passwordmanager.model.*;
import com.passwordmanager.service.*;
import com.passwordmanager.ui.SceneManager;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Controller for the 3-panel main vault screen.
 *
 * Left   : category / tag sidebar (TreeView)
 * Centre : entry list (ListView)
 * Right  : entry detail + action buttons (VBox)
 */
public class MainVaultController {

    // ── Toolbar ───────────────────────────────────────────────────────────────
    @FXML private TextField searchField;

    // ── Sidebar ───────────────────────────────────────────────────────────────
    @FXML private TreeView<String> categoryTree;

    // ── Entry list ────────────────────────────────────────────────────────────
    @FXML private ListView<EntryListItem> entryList;

    // ── Detail panel ──────────────────────────────────────────────────────────
    @FXML private VBox   detailPanel;
    @FXML private Label  detailTitle;
    @FXML private Label  detailType;
    @FXML private VBox   detailFields;
    @FXML private VBox   historyBox;
    @FXML private Label  detailUpdated;

    // ── Status bar ────────────────────────────────────────────────────────────
    @FXML private Label statusLabel;

    // ── Services ──────────────────────────────────────────────────────────────
    private final AuthService      auth      = AppContext.getInstance().getAuthService();
    private final VaultService     vault     = AppContext.getInstance().getVaultService();
    private final ClipboardManager clipboard = AppContext.getInstance().getClipboardManager();

    // ── State ─────────────────────────────────────────────────────────────────
    private final ObservableList<EntryListItem> entries = FXCollections.observableArrayList();
    private EntryListItem selectedItem;
    private ScheduledFuture<?> autoLockFuture;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auto-lock"); t.setDaemon(true); return t;
    });

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupEntryList();
        setupSidebar();
        setupSearch();
        loadEntries(null);
        scheduleAutoLock();
        detailPanel.setVisible(false);
    }

    // ── Toolbar actions ───────────────────────────────────────────────────────

    @FXML private void handleAddEntry() {
        AddEditEntryController.open(null, this::handleRefresh);
    }

    @FXML private void handleOpenAudit() {
        SceneManager.showAudit();
    }

    @FXML private void handleSettings() {
        SceneManager.showSettings();
    }

    @FXML private void handleLock() {
        clipboard.clearNow();
        auth.lock();
        SceneManager.showUnlockStep1();
    }

    // ── Entry list ────────────────────────────────────────────────────────────

    private void setupEntryList() {
        entryList.setItems(entries);
        entryList.setCellFactory(lv -> new EntryCell());
        entryList.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, item) -> showDetail(item));
    }

    private void loadEntries(String filter) {
        try {
            List<EntryListItem> list = (filter == null || filter.isBlank())
                    ? vault.listAll()
                    : vault.search(filter);
            entries.setAll(list);
        } catch (Exception e) {
            showStatus("Error loading entries: " + e.getMessage());
        }
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private void setupSidebar() {
        TreeItem<String> root = new TreeItem<>("Vault");
        root.setExpanded(true);

        root.getChildren().add(leaf("All Entries"));
        root.getChildren().add(leaf("Favourites"));

        try {
            TreeItem<String> catRoot = new TreeItem<>("Categories");
            catRoot.setExpanded(true);
            vault.listCategories().forEach(c -> catRoot.getChildren().add(leaf(c.getName())));
            root.getChildren().add(catRoot);
        } catch (Exception ignored) {}

        categoryTree.setRoot(root);
        categoryTree.setShowRoot(false);
        categoryTree.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (item == null || !item.isLeaf()) return;
            handleSidebarSelection(item.getValue());
        });
    }

    private void handleSidebarSelection(String label) {
        try {
            List<EntryListItem> list = switch (label) {
                case "All Entries"  -> vault.listAll();
                case "Favourites"   -> vault.listFavorites();
                default -> {
                    var cat = vault.listCategories().stream()
                            .filter(c -> c.getName().equals(label)).findFirst();
                    yield cat.isPresent() ? vault.listByCategory(cat.get().getId()) : vault.listAll();
                }
            };
            entries.setAll(list);
        } catch (Exception e) {
            showStatus("Error: " + e.getMessage());
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void setupSearch() {
        searchField.textProperty().addListener((obs, old, text) -> loadEntries(text));
    }

    // ── Detail panel ──────────────────────────────────────────────────────────

    private void showDetail(EntryListItem item) {
        if (item == null) { detailPanel.setVisible(false); return; }
        selectedItem = item;
        try {
            EntryDto dto = vault.getEntry(item.getId(), auth.getSessionKey());
            if (dto == null) return;
            detailPanel.setVisible(true);
            detailTitle.setText(dto.getTitle());
            detailType.setText(dto.getType().name());
            detailUpdated.setText("Updated: " + formatTs(dto.getUpdatedAt()));
            buildDetailFields(dto);
            buildHistory(dto);
        } catch (Exception e) {
            showStatus("Error loading entry: " + e.getMessage());
        }
    }

    private void buildDetailFields(EntryDto dto) {
        detailFields.getChildren().clear();
        dto.getFields().forEach((key, value) -> {
            boolean sensitive = isSensitive(key);
            HBox row = new HBox(8);
            row.getStyleClass().add("detail-row");

            Label keyLabel = new Label(friendlyKey(key) + ":");
            keyLabel.getStyleClass().add("detail-key");
            keyLabel.setPrefWidth(120);

            Label valLabel = new Label(sensitive ? "••••••••" : value);
            valLabel.getStyleClass().add("detail-value");
            valLabel.setWrapText(true);

            Button copyBtn = new Button("Copy");
            copyBtn.setOnAction(e -> copyField(value));

            HBox actions = new HBox(4, copyBtn);

            if (sensitive) {
                Button revealBtn = new Button("Show");
                revealBtn.setOnAction(e -> {
                    boolean shown = valLabel.getText().startsWith("•");
                    valLabel.setText(shown ? value : "••••••••");
                    revealBtn.setText(shown ? "Hide" : "Show");
                });
                actions.getChildren().add(revealBtn);
            }

            row.getChildren().addAll(keyLabel, valLabel, actions);
            HBox.setHgrow(valLabel, Priority.ALWAYS);
            detailFields.getChildren().add(row);
        });

        if (!dto.getTags().isEmpty()) {
            Label tagsLabel = new Label("Tags: " + String.join(", ", dto.getTags()));
            tagsLabel.getStyleClass().add("detail-tags");
            detailFields.getChildren().add(tagsLabel);
        }
    }

    private void buildHistory(EntryDto dto) {
        historyBox.getChildren().clear();
        if (dto.getType() != EntryType.LOGIN) return;
        try {
            var history = vault.getHistory(dto.getId(), auth.getSessionKey());
            if (history.isEmpty()) return;
            Label hdr = new Label("Password History");
            hdr.getStyleClass().add("section-header");
            historyBox.getChildren().add(hdr);
            history.forEach(h -> {
                Label entry = new Label(formatTs(h.getChangedAt()) + "  ••••••••");
                entry.getStyleClass().add("history-entry");
                historyBox.getChildren().add(entry);
            });
        } catch (Exception ignored) {}
    }

    // ── Entry actions (called from list cells via context menu) ───────────────

    @FXML private void handleEditSelected() {
        if (selectedItem == null) return;
        AddEditEntryController.open(selectedItem.getId(), this::handleRefresh);
    }

    @FXML private void handleDeleteSelected() {
        if (selectedItem == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + selectedItem.getTitle() + "\"?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm delete");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    vault.deleteEntry(selectedItem.getId());
                    handleRefresh();
                    detailPanel.setVisible(false);
                } catch (Exception e) {
                    showStatus("Delete failed: " + e.getMessage());
                }
            }
        });
    }

    void handleRefresh() {
        loadEntries(searchField.getText());
        setupSidebar();
    }

    // ── Clipboard ─────────────────────────────────────────────────────────────

    private void copyField(String value) {
        int delay = ConfigLoader.getInt("security.clipboardClearSeconds", 30);
        clipboard.copy(value, delay, remaining ->
                showStatus(remaining > 0
                        ? "Clipboard clears in " + remaining + "s"
                        : "Clipboard cleared"));
        showStatus("Copied! Clipboard clears in " + delay + "s");
    }

    // ── Auto-lock ─────────────────────────────────────────────────────────────

    private void scheduleAutoLock() {
        int seconds = ConfigLoader.getInt("security.autoLockSeconds", 300);
        autoLockFuture = scheduler.schedule(() ->
                Platform.runLater(this::handleLock), seconds, TimeUnit.SECONDS);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }

    private TreeItem<String> leaf(String label) {
        return new TreeItem<>(label);
    }

    private String formatTs(long epochMillis) {
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMillis),
                ZoneId.systemDefault()).format(DATE_FMT);
    }

    private boolean isSensitive(String key) {
        return Set.of("password", "cvv", "pin", "totp").contains(key.toLowerCase());
    }

    private String friendlyKey(String key) {
        return switch (key) {
            case "username"      -> "Username";
            case "password"      -> "Password";
            case "url"           -> "URL";
            case "notes"         -> "Notes";
            case "body"          -> "Note";
            case "totp"          -> "TOTP Secret";
            case "card_number"   -> "Card Number";
            case "expiry"        -> "Expiry";
            case "cvv"           -> "CVV";
            case "pin"           -> "PIN";
            case "cardholder"    -> "Cardholder";
            case "full_name"     -> "Full Name";
            case "dob"           -> "Date of Birth";
            case "passport"      -> "Passport No.";
            case "national_id"   -> "National ID";
            default              -> key;
        };
    }

    // ── Custom list cell ──────────────────────────────────────────────────────

    private class EntryCell extends ListCell<EntryListItem> {
        @Override
        protected void updateItem(EntryListItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            HBox row = new HBox(8);
            row.getStyleClass().add("entry-cell");

            Label typeIcon = new Label(typeEmoji(item.getType()));
            typeIcon.getStyleClass().add("entry-icon");

            VBox info = new VBox(2);
            Label title = new Label(item.getTitle());
            title.getStyleClass().add("entry-title");
            Label sub = new Label(item.getCategoryName() != null ? item.getCategoryName() : "");
            sub.getStyleClass().add("entry-sub");
            info.getChildren().addAll(title, sub);

            Label star = new Label(item.isFavorite() ? "★" : "☆");
            star.getStyleClass().add("entry-star");
            star.setOnMouseClicked(e -> {
                try {
                    vault.toggleFavorite(item.getId(), !item.isFavorite());
                    handleRefresh();
                } catch (Exception ex) { showStatus("Error: " + ex.getMessage()); }
            });

            row.getChildren().addAll(typeIcon, info, star);
            HBox.setHgrow(info, Priority.ALWAYS);

            // Context menu
            ContextMenu menu = new ContextMenu();
            MenuItem edit   = new MenuItem("Edit");
            MenuItem delete = new MenuItem("Delete");
            edit.setOnAction(e -> AddEditEntryController.open(item.getId(), MainVaultController.this::handleRefresh));
            delete.setOnAction(e -> { entryList.getSelectionModel().select(item); handleDeleteSelected(); });
            menu.getItems().addAll(edit, delete);
            setContextMenu(menu);

            setGraphic(row);
        }

        private String typeEmoji(EntryType type) {
            return switch (type) {
                case LOGIN    -> "🔑";
                case NOTE     -> "📝";
                case CARD     -> "💳";
                case IDENTITY -> "🪪";
            };
        }
    }
}
