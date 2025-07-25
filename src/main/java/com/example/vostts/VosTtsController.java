package com.example.vostts;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.animation.FadeTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Modality;

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
import java.util.logging.Level;
import java.util.logging.Logger;

public class VosTtsController {
    private static final Logger LOG = Logger.getLogger(VosTtsController.class.getName());
    @FXML private Label sessionLabel; // may be null in new UI
    @FXML private Button startButton;
    @FXML private Button pauseButton;
    @FXML private Label timerLabel;
    @FXML private Label partialLabel;
    @FXML private VBox transcriptBox;
    @FXML private ComboBox<Mixer.Info> deviceCombo;
    
    private Stage browserStage;

    private final Deque<Label> lines = new ArrayDeque<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> transcriptionTask;
    private boolean running = false;
    private BufferedWriter writer;
    private File modelDir;
    private boolean modelReady = false;
    private String currentSessionId = "-";
    private boolean paused = false;
    private Timeline timer;
    private long startTime;
    private long pauseAccum;
    private long pauseStarted;
    private boolean darkMode = true;

    @FXML
    private void initialize() {
        updateSession("-");
        loadInputDevices();
        startButton.setDisable(true);
        if (timerLabel != null) {
            timerLabel.setText("00:00:00");
        }
        if (partialLabel != null) {
            partialLabel.setText("");
            partialLabel.setWrapText(true);
            partialLabel.setAlignment(Pos.CENTER);
            partialLabel.setMaxWidth(Double.MAX_VALUE);
        }
        LOG.fine("Controller initialised");
    }

    @FXML
    private void onStart() {
        if (running) {
            LOG.info("Stopping transcription session");
            stopTranscription();
        } else {
            LOG.info("Starting transcription session");
            startTranscription();
        }
    }

    @FXML
    private void onBrowse() {
        if (browserStage != null && browserStage.isShowing()) {
            browserStage.requestFocus();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/vostts/browser.fxml"));
            Parent root = loader.load();
            browserStage = new Stage();
            browserStage.initModality(Modality.NONE);
            browserStage.setTitle("Transcription Browser");
            Scene scene = new Scene(root, 600, 450);
            scene.getStylesheets().add(getClass().getResource("/com/example/vostts/dark.css").toExternalForm());
            browserStage.setScene(scene);
            browserStage.show();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to open browser: " + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    @FXML
    private void onSettings() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Settings not implemented in demo.", ButtonType.OK);
        alert.showAndWait();
    }

    @FXML
    private void onToggleTheme() {
        Scene scene = startButton.getScene();
        if (scene == null) return;
        String dark = getClass().getResource("/com/example/vostts/dark.css").toExternalForm();
        String light = getClass().getResource("/com/example/vostts/light.css").toExternalForm();
        if (darkMode) {
            scene.getStylesheets().remove(dark);
            scene.getStylesheets().add(light);
        } else {
            scene.getStylesheets().remove(light);
            scene.getStylesheets().add(dark);
        }
        darkMode = !darkMode;
    }

    @FXML
    private void onPauseResume() {
        if (transcriptionTask == null) return;
        if (paused) {
            pauseAccum += System.currentTimeMillis() - pauseStarted;
            paused = false;
            running = true;
            if (timer != null) timer.play();
            pauseButton.setText("Paused");
        } else {
            paused = true;
            running = false;
            pauseStarted = System.currentTimeMillis();
            if (timer != null) timer.pause();
            pauseButton.setText("Resume");
        }
    }

    @FXML
    private void onSave() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Transcript automatically saved to session folder.", ButtonType.OK);
        alert.showAndWait();
    }

    @FXML
    private void onAutoStop() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Auto stop configuration not implemented.", ButtonType.OK);
        alert.showAndWait();
    }

    private void startTranscription() {
        if (!modelReady) {
            LOG.warning("Attempted to start transcription before model ready");
            return;
        }
        updateSession(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        startButton.setText("Stop");
        pauseButton.setText("Paused");
        if (deviceCombo != null) {
            deviceCombo.setDisable(true);
        }
        running = true;
        paused = false;
        pauseAccum = 0;
        startTime = System.currentTimeMillis();
        if (timerLabel != null) {
            timerLabel.setText("00:00:00");
            timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateTimer()));
            timer.setCycleCount(Timeline.INDEFINITE);
            timer.play();
        }
        if (partialLabel != null) {
            partialLabel.setText("");
        }
        LOG.info("Transcription started");
        transcriptionTask = executor.submit(this::runRecognition);
    }

    private void stopTranscription() {
        running = false;
        LOG.info("Transcription stopping");
        if (transcriptionTask != null) {
            transcriptionTask.cancel(true);
        }
        // Writer is closed in the recognition thread's try-with-resources block.
        // Clearing the reference here avoids further writes until a new session
        // starts but prevents closing the stream while it may still be in use.
        writer = null;
        startButton.setText("Start");
        if (pauseButton != null) {
            pauseButton.setText("Paused");
        }
        if (timer != null) {
            timer.stop();
        }
        if (timerLabel != null) {
            timerLabel.setText("00:00:00");
        }
        if (deviceCombo != null) {
            deviceCombo.setDisable(false);
        }
        if (partialLabel != null) {
            partialLabel.setText("");
        }
    }

    private void updateSession(String id) {
        currentSessionId = id;
        if (sessionLabel != null) {
            sessionLabel.setText("Session: " + id);
        }
        LOG.fine(() -> "Session updated: " + id);
    }

    private void runRecognition() {
        Path base = Paths.get(System.getProperty("user.home"), "vos-stt", "sessions", currentSessionId);
        base.toFile().mkdirs();
        File outFile = base.resolve("transcript.txt").toFile();
        LOG.fine(() -> "Writing transcript to " + outFile.getAbsolutePath());
        try (Model model = new Model(locateModelPath(modelDir).getAbsolutePath());
             BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
            writer = bw;
            Recognizer recognizer = new Recognizer(model, 16000.0f);
            AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            Mixer.Info selected = null;
            if (deviceCombo != null) {
                selected = deviceCombo.getSelectionModel().getSelectedItem();
            }
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
            LOG.fine("Recognition loop started");
            while (!Thread.currentThread().isInterrupted()) {
                int n = line.read(buffer, 0, buffer.length);
                if (n < 0) break;
                if (running) {
                    if (recognizer.acceptWaveForm(buffer, n)) {
                        String result = recognizer.getResult();
                        handleResult(result);
                    } else {
                        String partial = recognizer.getPartialResult();
                        handlePartial(partial);
                    }
                }
            }
            line.stop();
            line.close();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Recognition error", ex);
        } finally {
            Platform.runLater(() -> {
                running = false;
                startButton.setText("Start");
                if (pauseButton != null) {
                    pauseButton.setText("Paused");
                }
                if (deviceCombo != null) {
                    deviceCombo.setDisable(false);
                }
            });
            // Ensure the writer reference is cleared after the session ends.
            writer = null;
            LOG.fine("Recognition loop finished");
        }
    }

    private void handleResult(String json) throws IOException {
        JSONObject obj = new JSONObject(json);
        String text = obj.optString("text");
        if (!text.isEmpty()) {
            LOG.fine(() -> "Recognised: " + text);
            writeLine(text);
            if (partialLabel != null) {
                Platform.runLater(() -> partialLabel.setText(text));
            }
        }
    }

    private void handlePartial(String json) {
        if (partialLabel == null) return;
        JSONObject obj = new JSONObject(json);
        String partial = obj.optString("partial");
        if (!partial.isEmpty()) {
            Platform.runLater(() -> partialLabel.setText(partial));
        }
    }

    private void updateTimer() {
        if (timerLabel == null) return;
        long now = System.currentTimeMillis();
        long elapsed = now - startTime - pauseAccum - (paused ? (now - pauseStarted) : 0);
        long secs = elapsed / 1000;
        long h = secs / 3600;
        long m = (secs % 3600) / 60;
        long s = secs % 60;
        timerLabel.setText(String.format("%02d:%02d:%02d", h, m, s));
    }

    private void writeLine(String text) {
        Platform.runLater(() -> {
            Label line = new Label(text);
            line.setWrapText(true);
            line.setAlignment(Pos.CENTER);
            line.setMaxWidth(Double.MAX_VALUE);
            line.getStyleClass().add("transcript-new");
            transcriptBox.getChildren().add(line);
            FadeTransition ft = new FadeTransition(Duration.millis(300), line);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
            lines.addLast(line);
            while (lines.size() > 3) {
                Label old = lines.removeFirst();
                transcriptBox.getChildren().remove(old);
            }
            Label[] arr = lines.toArray(new Label[0]);
            for (int i = 0; i < arr.length; i++) {
                if (i == arr.length - 1) {
                    arr[i].getStyleClass().setAll("transcript-new");
                } else {
                    arr[i].getStyleClass().setAll("transcript-old");
                }
            }
        });
        if (writer != null) {
            try {
                writer.write(text);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed writing line", e);
            }
        }
    }

    private void loadInputDevices() {
        if (deviceCombo == null) {
            return;
        }
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
        LOG.fine(() -> "Loaded " + deviceCombo.getItems().size() + " input devices");
    }

    /**
     * Create a download task for the Vosk model. The task updates its progress
     * as bytes are downloaded and unzipped.
     */
    public Task<Void> createModelDownloadTask(File targetDir) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                String url = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip";
                LOG.info("Downloading model from " + url);
                targetDir.getParentFile().mkdirs();
                Path zipPath = targetDir.toPath().resolveSibling("vosk-model-en-us-0.22.zip");
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                int length = conn.getContentLength();
                updateMessage("0 MB/s");
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(zipPath.toFile())) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long total = 0;
                    long lastTotal = 0;
                    long lastTime = System.nanoTime();
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        total += bytesRead;
                        if (length > 0) {
                            updateProgress(total, length);
                        }
                        long now = System.nanoTime();
                        if (now - lastTime > 1_000_000_000L) {
                            double mb = (total - lastTotal) / (1024.0 * 1024.0);
                            double mbps = mb / ((now - lastTime) / 1_000_000_000.0);
                            updateMessage(String.format("%.1f MB/s", mbps));
                            lastTime = now;
                            lastTotal = total;
                        }
                    }
                }
                unzip(zipPath.toFile(), targetDir);
                Files.delete(zipPath);
                if (!isModelValid(targetDir)) {
                    throw new IOException("Downloaded model is invalid");
                }
                LOG.info("Model download complete");
                return null;
            }
        };
    }

    public static void unzip(File zipFile, File targetDir) throws IOException {
        targetDir.mkdirs();
        LOG.fine(() -> "Unzipping " + zipFile + " to " + targetDir);
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

    private static File locateModelPath(File dir) {
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

    public static boolean isModelValid(File dir) {
        File path = locateModelPath(dir);
        return new File(path, "am").exists();
    }

    /** Set the directory containing the speech model. */
    public void setModelDir(File dir) {
        this.modelDir = locateModelPath(dir);
        LOG.fine(() -> "Model directory set to " + this.modelDir);
    }

    /**
     * Mark the speech model as ready and enable the start button accordingly.
     */
    public void setModelReady(boolean ready) {
        this.modelReady = ready;
        startButton.setDisable(!ready);
        LOG.fine(() -> "Model ready: " + ready);
    }
}
