package com.example.vostts;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
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
    @FXML private Label sessionLabel;
    @FXML private Button startButton;
    @FXML private Button pauseButton;
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

    @FXML
    private void initialize() {
        updateSession("-");
        loadInputDevices();
        startButton.setDisable(true);
        if (partialLabel != null) {
            partialLabel.setText("");
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
    private void onPause() {
        running = !running;
        LOG.fine(() -> "Paused state: " + !running);
        pauseButton.setText(running ? "Pause" : "Resume");
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

    private void startTranscription() {
        if (!modelReady) {
            LOG.warning("Attempted to start transcription before model ready");
            return;
        }
        updateSession(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        startButton.setText("Stop");
        pauseButton.setDisable(false);
        running = true;
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
        startButton.setText("Start Live Transcription");
        pauseButton.setDisable(true);
        if (partialLabel != null) {
            partialLabel.setText("");
        }
    }

    private void updateSession(String id) {
        sessionLabel.setText("Session: " + id);
        LOG.fine(() -> "Session updated: " + id);
    }

    private void runRecognition() {
        File outFile = new File("transcript_" + sessionLabel.getText() + ".txt");
        LOG.fine(() -> "Writing transcript to " + outFile.getAbsolutePath());
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
                startButton.setText("Start Live Transcription");
                pauseButton.setDisable(true);
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
                LOG.log(Level.WARNING, "Failed writing line", e);
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
