package com.example.vostts;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Controller for the modal transcript viewer. */
public class TranscriptViewerController {
    @FXML private Label nameLabel;
    @FXML private Label dateLabel;
    @FXML private TextArea textArea;
    @FXML private Button closeButton;

    private SessionMetadata session;

    public void loadSession(SessionMetadata session) {
        this.session = session;
        nameLabel.setText(session.getName());
        dateLabel.setText(session.getDate());
        Path file = session.getDirectory().resolve("transcript.txt");
        try {
            String content = Files.exists(file) ? Files.readString(file) : "";
            textArea.setText(content);
        } catch (IOException e) {
            showError("Failed to load transcript: " + e.getMessage());
        }
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, javafx.scene.control.ButtonType.OK);
        alert.showAndWait();
    }
}
