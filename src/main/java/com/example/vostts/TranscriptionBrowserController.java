package com.example.vostts;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/** Controller for the Transcription Browser window. */
public class TranscriptionBrowserController {
    @FXML private TableView<SessionMetadata> table;
    @FXML private TableColumn<SessionMetadata, String> nameColumn;
    @FXML private TableColumn<SessionMetadata, String> dateColumn;
    @FXML private TableColumn<SessionMetadata, String> durationColumn;
    @FXML private TableColumn<SessionMetadata, String> statusColumn;

    private final ObservableList<SessionMetadata> sessions = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        durationColumn.setCellValueFactory(new PropertyValueFactory<>("duration"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        table.setItems(sessions);
        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void refresh() {
        sessions.clear();
        Path base = Paths.get(System.getProperty("user.home"), "vos-stt", "sessions");
        if (Files.isDirectory(base)) {
            try {
                List<SessionMetadata> list = Files.list(base)
                        .filter(Files::isDirectory)
                        .map(p -> {
                            try {
                                return SessionMetadata.load(p);
                            } catch (IOException e) {
                                return null;
                            }
                        })
                        .filter(m -> m != null)
                        .collect(Collectors.toList());
                sessions.addAll(list);
            } catch (IOException e) {
                showError("Failed to read sessions: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onOpen() {
        SessionMetadata selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/vostts/viewer.fxml"));
            Parent root = loader.load();
            TranscriptViewerController controller = loader.getController();
            controller.loadSession(selected);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UNDECORATED);
            Scene scene = new Scene(root, 600, 400);
            scene.getStylesheets().add(getClass().getResource("/com/example/vostts/dark.css").toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e) {
            showError("Failed to open session: " + e.getMessage());
        }
    }

    @FXML
    private void onDelete() {
        SessionMetadata selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            Files.walk(selected.getDirectory())
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            refresh();
        } catch (IOException e) {
            showError("Failed to delete session: " + e.getMessage());
        }
    }

    @FXML
    private void onExport() {
        // Placeholder: real export not implemented
        showError("Export not implemented in demo.");
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) table.getScene().getWindow();
        stage.close();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, javafx.scene.control.ButtonType.OK);
        alert.showAndWait();
    }
}
