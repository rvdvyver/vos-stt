package com.example.vostts;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VosTtsController {
    @FXML private Label sessionLabel;
    @FXML private Button startButton;
    @FXML private Button pauseButton;
    @FXML private VBox transcriptBox;
    @FXML private ComboBox<String> exportCombo;

    private final Deque<Label> lines = new ArrayDeque<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> transcriptionTask;
    private boolean running = false;
    private BufferedWriter writer;

    @FXML
    private void initialize() {
        exportCombo.getItems().addAll("TXT", "DOCX", "SRT");
        updateSession("-");
    }

    @FXML
    private void onStart() {
        if (running) {
            stopTranscription();
        } else {
            startTranscription();
        }
    }

    @FXML
    private void onPause() {
        running = !running;
        pauseButton.setText(running ? "Pause" : "Resume");
    }

    private void startTranscription() {
        updateSession(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        startButton.setText("Stop");
        pauseButton.setDisable(false);
        running = true;
        try {
            writer = new BufferedWriter(new FileWriter("transcript_" + sessionLabel.getText() + ".txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        transcriptionTask = executor.submit(this::mockTranscription);
    }

    private void stopTranscription() {
        running = false;
        if (transcriptionTask != null) {
            transcriptionTask.cancel(true);
        }
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        startButton.setText("Start Live Transcription");
        pauseButton.setDisable(true);
    }

    private void updateSession(String id) {
        sessionLabel.setText("Session: " + id);
    }

    private void mockTranscription() {
        int count = 0;
        while (!Thread.currentThread().isInterrupted()) {
            if (running) {
                String text = "Line " + (++count);
                writeLine(text);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void writeLine(String text) {
        Platform.runLater(() -> {
            Label line = new Label(text);
            line.setStyle("-fx-font-size: 16pt; -fx-font-weight: bold;");
            transcriptBox.getChildren().add(line);
            lines.addLast(line);
            if (lines.size() > 3) {
                Label old = lines.removeFirst();
                transcriptBox.getChildren().remove(old);
            }
        });
        if (writer != null) {
            try {
                writer.write(text);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
