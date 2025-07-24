package com.example.vostts;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.json.JSONObject;

import javax.sound.sampled.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
    @FXML private ComboBox<Mixer.Info> deviceCombo;

    private final Deque<Label> lines = new ArrayDeque<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> transcriptionTask;
    private boolean running = false;
    private BufferedWriter writer;
    private File modelDir;
    private boolean modelReady = false;

    @FXML
    private void initialize() {
        updateSession("-");
        loadInputDevices();
        startButton.setDisable(true);
        ensureModel();
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
        if (!modelReady) {
            return;
        }
        updateSession(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        startButton.setText("Stop");
        pauseButton.setDisable(false);
        running = true;
        transcriptionTask = executor.submit(this::runRecognition);
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

    private void runRecognition() {
        File outFile = new File("transcript_" + sessionLabel.getText() + ".txt");
        try (Model model = new Model(locateModelPath(modelDir).getAbsolutePath());
             BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
            writer = bw;
            Recognizer recognizer = new Recognizer(model, 16000.0f);
            AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            Mixer.Info selected = deviceCombo.getSelectionModel().getSelectedItem();
            TargetDataLine line;
            if (selected != null) {
                Mixer mixer = AudioSystem.getMixer(selected);
                line = (TargetDataLine) mixer.getLine(info);
            } else {
                line = (TargetDataLine) AudioSystem.getLine(info);
            }
            line.open(format);
            line.start();
            byte[] buffer = new byte[4096];
            while (!Thread.currentThread().isInterrupted()) {
                int n = line.read(buffer, 0, buffer.length);
                if (n < 0) break;
                if (running) {
                    if (recognizer.acceptWaveForm(buffer, n)) {
                        String result = recognizer.getResult();
                        handleResult(result);
                    }
                }
            }
            line.stop();
            line.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            Platform.runLater(() -> {
                running = false;
                startButton.setText("Start Live Transcription");
                pauseButton.setDisable(true);
            });
        }
    }

    private void handleResult(String json) throws IOException {
        JSONObject obj = new JSONObject(json);
        String text = obj.optString("text");
        if (!text.isEmpty()) {
            writeLine(text);
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

    private void loadInputDevices() {
        deviceCombo.getItems().clear();
        AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mi);
            if (mixer.isLineSupported(info)) {
                deviceCombo.getItems().add(mi);
            }
        }
        if (!deviceCombo.getItems().isEmpty()) {
            deviceCombo.getSelectionModel().selectFirst();
        }
    }

    private void ensureModel() {
        modelDir = new File("models/vosk-model-en-us-0.22");
        if (!isModelValid(modelDir)) {
            showDownloadSplash();
        } else {
            modelReady = true;
            startButton.setDisable(false);
        }
    }

    private void showDownloadSplash() {
        ProgressBar bar = new ProgressBar(0);
        Label label = new Label("Downloading model...");
        VBox box = new VBox(10, label, bar);
        box.setStyle("-fx-padding: 20;");
        Stage stage = new Stage();
        stage.setScene(new Scene(box));
        stage.setTitle("Model Download");
        stage.show();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String url = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip";
                modelDir.getParentFile().mkdirs();
                Path zipPath = modelDir.toPath().resolveSibling("vosk-model-en-us-0.22.zip");
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                int length = conn.getContentLength();
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(zipPath.toFile())) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long total = 0;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        total += bytesRead;
                        if (length > 0) {
                            updateProgress(total, length);
                        }
                    }
                }
                unzip(zipPath.toFile(), modelDir);
                Files.delete(zipPath);
                return null;
            }
        };
        bar.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(e -> {
            stage.close();
            modelReady = isModelValid(modelDir);
            startButton.setDisable(!modelReady);
        });
        executor.submit(task);
    }

    private void unzip(File zipFile, File targetDir) throws IOException {
        targetDir.mkdirs();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private File locateModelPath(File dir) {
        if (new File(dir, "am").exists()) {
            return dir;
        }
        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null && subDirs.length == 1) {
            File candidate = subDirs[0];
            if (new File(candidate, "am").exists()) {
                return candidate;
            }
        }
        return dir;
    }

    private boolean isModelValid(File dir) {
        File path = locateModelPath(dir);
        return new File(path, "am").exists();
    }
}
