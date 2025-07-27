package com.example.vostts;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/** Controller for the monitor window listing processed text files. */
public class MonitorController {
    @FXML
    private ListView<String> listView;

    @FXML
    private void initialize() {
        refresh();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onClose() {
        Stage stage = (Stage) listView.getScene().getWindow();
        stage.close();
    }

    private void refresh() {
        listView.getItems().clear();
        Path base = Paths.get(System.getProperty("user.home"), "vos-stt", "sessions");
        if (Files.isDirectory(base)) {
            try {
                List<String> files = Files.list(base)
                        .filter(Files::isDirectory)
                        .map(p -> p.resolve("transcript.srt"))
                        .filter(Files::exists)
                        .map(Path::toString)
                        .collect(Collectors.toList());
                listView.getItems().addAll(files);
            } catch (IOException e) {
                showError("Failed to read sessions: " + e.getMessage());
            }
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, javafx.scene.control.ButtonType.OK);
        alert.showAndWait();
    }
}
