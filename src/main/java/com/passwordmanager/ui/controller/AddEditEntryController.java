package com.passwordmanager.ui.controller;

import com.passwordmanager.model.*;
import com.passwordmanager.service.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.*;

import java.util.*;

/**
 * Add / Edit entry dialog.
 * Opens as a modal window. Supports all four entry types.
 */
public class AddEditEntryController {

    @FXML private ComboBox<EntryType> typeCombo;
    @FXML private TextField           titleField;
    @FXML private TextField           categoryField;
    @FXML private TextField           tagsField;
    @FXML private CheckBox            favoriteCheck;
    @FXML private Label               errorLabel;

    // Type-specific field panes (only one visible at a time)
    @FXML private VBox loginPane;
    @FXML private VBox notePane;
    @FXML private VBox cardPane;
    @FXML private VBox identityPane;

    // LOGIN fields
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     urlField;
    @FXML private TextField     totpField;
    @FXML private TextArea      loginNotesArea;

    // NOTE fields
    @FXML private TextArea noteBodyArea;

    // CARD fields
    @FXML private TextField cardholderField;
    @FXML private TextField cardNumberField;
    @FXML private TextField expiryField;
    @FXML private TextField cvvField;
    @FXML private TextField pinField;
    @FXML private TextArea  cardNotesArea;

    // IDENTITY fields
    @FXML private TextField fullNameField;
    @FXML private TextField dobField;
    @FXML private TextField passportField;
    @FXML private TextField nationalIdField;
    @FXML private TextArea  identityNotesArea;

    private final AuthService      auth    = AppContext.getInstance().getAuthService();
    private final VaultService     vault   = AppContext.getInstance().getVaultService();
    private final PasswordGenerator gen    = AppContext.getInstance().getPasswordGenerator();

    private Long   entryId;       // null = new entry
    private Runnable onSave;

    // ── Static opener ─────────────────────────────────────────────────────────

    public static void open(Long entryId, Runnable onSave) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    AddEditEntryController.class.getResource(
                            "/com/passwordmanager/fxml/add_edit_entry.fxml"));
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(entryId == null ? "Add Entry" : "Edit Entry");
            stage.setScene(new Scene(loader.load(), 520, 620));
            AddEditEntryController ctrl = loader.getController();
            ctrl.entryId = entryId;
            ctrl.onSave  = onSave;
            ctrl.loadEntryData();
            stage.showAndWait();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Could not open dialog: " + e.getMessage()).showAndWait();
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        typeCombo.getItems().setAll(EntryType.values());
        typeCombo.setValue(EntryType.LOGIN);
        typeCombo.setOnAction(e -> switchPane(typeCombo.getValue()));
        switchPane(EntryType.LOGIN);
    }

    private void loadEntryData() {
        if (entryId == null) return;
        try {
            EntryDto dto = vault.getEntry(entryId, auth.getSessionKey());
            if (dto == null) return;
            typeCombo.setValue(dto.getType());
            typeCombo.setDisable(true);  // can't change type on existing entry
            titleField.setText(dto.getTitle());
            categoryField.setText(dto.getCategoryName() != null ? dto.getCategoryName() : "");
            tagsField.setText(String.join(", ", dto.getTags()));
            favoriteCheck.setSelected(dto.isFavorite());
            switchPane(dto.getType());
            populateFields(dto);
        } catch (Exception e) {
            errorLabel.setText("Failed to load entry: " + e.getMessage());
        }
    }

    private void populateFields(EntryDto dto) {
        switch (dto.getType()) {
            case LOGIN -> {
                usernameField.setText(dto.getField("username"));
                passwordField.setText(dto.getField("password"));
                urlField.setText(dto.getField("url"));
                totpField.setText(dto.getField("totp"));
                loginNotesArea.setText(dto.getField("notes"));
            }
            case NOTE -> noteBodyArea.setText(dto.getField("body"));
            case CARD -> {
                cardholderField.setText(dto.getField("cardholder"));
                cardNumberField.setText(dto.getField("card_number"));
                expiryField.setText(dto.getField("expiry"));
                cvvField.setText(dto.getField("cvv"));
                pinField.setText(dto.getField("pin"));
                cardNotesArea.setText(dto.getField("notes"));
            }
            case IDENTITY -> {
                fullNameField.setText(dto.getField("full_name"));
                dobField.setText(dto.getField("dob"));
                passportField.setText(dto.getField("passport"));
                nationalIdField.setText(dto.getField("national_id"));
                identityNotesArea.setText(dto.getField("notes"));
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @FXML
    private void handleGeneratePassword() {
        String pw = gen.generate(20, true, true, true, true);
        passwordField.setText(pw);
    }

    @FXML
    private void handleSave() {
        errorLabel.setText("");
        if (titleField.getText().isBlank()) {
            errorLabel.setText("Title is required."); return;
        }
        try {
            EntryDto dto = buildDto();
            if (entryId == null) {
                vault.createEntry(dto, auth.getSessionKey());
            } else {
                vault.updateEntry(dto, auth.getSessionKey());
            }
            if (onSave != null) onSave.run();
            close();
        } catch (Exception e) {
            errorLabel.setText("Save failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() { close(); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EntryDto buildDto() {
        EntryDto dto = new EntryDto();
        if (entryId != null) dto.setId(entryId);
        dto.setType(typeCombo.getValue());
        dto.setTitle(titleField.getText().trim());
        dto.setCategoryName(categoryField.getText().trim());
        dto.setFavorite(favoriteCheck.isSelected());

        String tagsRaw = tagsField.getText();
        if (!tagsRaw.isBlank()) {
            dto.setTags(Arrays.stream(tagsRaw.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList());
        }

        switch (dto.getType()) {
            case LOGIN -> {
                dto.setField("username", usernameField.getText());
                dto.setField("password", passwordField.getText());
                dto.setField("url",      urlField.getText());
                dto.setField("totp",     totpField.getText());
                dto.setField("notes",    loginNotesArea.getText());
            }
            case NOTE -> dto.setField("body", noteBodyArea.getText());
            case CARD -> {
                dto.setField("cardholder",  cardholderField.getText());
                dto.setField("card_number", cardNumberField.getText());
                dto.setField("expiry",      expiryField.getText());
                dto.setField("cvv",         cvvField.getText());
                dto.setField("pin",         pinField.getText());
                dto.setField("notes",       cardNotesArea.getText());
            }
            case IDENTITY -> {
                dto.setField("full_name",  fullNameField.getText());
                dto.setField("dob",        dobField.getText());
                dto.setField("passport",   passportField.getText());
                dto.setField("national_id",nationalIdField.getText());
                dto.setField("notes",      identityNotesArea.getText());
            }
        }
        return dto;
    }

    private void switchPane(EntryType type) {
        loginPane.setVisible(type == EntryType.LOGIN);    loginPane.setManaged(type == EntryType.LOGIN);
        notePane.setVisible(type == EntryType.NOTE);      notePane.setManaged(type == EntryType.NOTE);
        cardPane.setVisible(type == EntryType.CARD);      cardPane.setManaged(type == EntryType.CARD);
        identityPane.setVisible(type == EntryType.IDENTITY); identityPane.setManaged(type == EntryType.IDENTITY);
    }

    private void close() {
        ((Stage) titleField.getScene().getWindow()).close();
    }
}
