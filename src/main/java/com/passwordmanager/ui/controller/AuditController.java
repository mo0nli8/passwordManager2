package com.passwordmanager.ui.controller;

import com.passwordmanager.model.AuditResult;
import com.passwordmanager.service.*;
import com.passwordmanager.ui.SceneManager;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class AuditController {

    @FXML private Label                              summaryLabel;
    @FXML private TableView<AuditResult>             auditTable;
    @FXML private TableColumn<AuditResult, String>   titleCol;
    @FXML private TableColumn<AuditResult, String>   issueCol;
    @FXML private Button                             runButton;
    @FXML private Label                              statusLabel;

    private final AuthService  auth  = AppContext.getInstance().getAuthService();
    private final AuditService audit = AppContext.getInstance().getAuditService();

    private final ObservableList<AuditResult> results = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        titleCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getEntryTitle()));
        issueCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getIssueLabel()));
        auditTable.setItems(results);
        runAudit();
    }

    @FXML
    private void runAudit() {
        runButton.setDisable(true);
        statusLabel.setText("Running audit…");
        results.clear();

        new Thread(() -> {
            try {
                List<AuditResult> found = audit.run(auth.getSessionKey());
                javafx.application.Platform.runLater(() -> {
                    results.setAll(found);
                    summaryLabel.setText(found.isEmpty()
                            ? "No issues found – your vault looks healthy!"
                            : found.size() + " issue(s) found");
                    statusLabel.setText("");
                    runButton.setDisable(false);
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Audit failed: " + e.getMessage());
                    runButton.setDisable(false);
                });
            }
        }, "audit-thread").start();
    }

    @FXML
    private void handleClose() {
        SceneManager.showMainVault();
    }
}
