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
import javafx.stage.StageStyle;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.json.JSONObject;

import com.example.vostts.SettingsController;
import com.example.vostts.ThemeManager;
import com.example.vostts.DragUtil;

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
    @FXML private Button pauseButton; // may be null in new UI
    @FXML private Label timerLabel;
    @FXML private Label partialLabel;
    @FXML private VBox transcriptBox;
    @FXML private Button settingsButton;

    private Mixer.Info selectedDevice;
    private int timeoutSeconds = 0;
    private Timeline autoStop;
    
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
    /** Timestamp in ms marking the start of the current subtitle segment. */
    private long lastSegmentTime;
    /** Subtitle index counter for SRT output. */
    private int srtIndex;
    /** Maximum characters before inserting a line break. */
    private int wrapChars = 35;

    @FXML
    private void initialize() {
        updateSession("-");
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
            browserStage.initStyle(StageStyle.UNDECORATED);
            Scene scene = new Scene(root, 600, 450);
            ThemeManager.apply(scene);
            DragUtil.makeDraggable(browserStage, root);
            browserStage.setScene(scene);
            browserStage.show();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to open browser: " + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    @FXML
    private void onSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/vostts/settings.fxml"));
            Parent root = loader.load();
            SettingsController sc = loader.getController();
            sc.setParent(this);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UNDECORATED);
            Scene scene = new Scene(root, 300, 200);
            ThemeManager.apply(scene);
            DragUtil.makeDraggable(stage, root);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to open settings: " + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    @FXML
    private void onCloseApp() {
        if (running) {
            stopTranscription();
        }
        Platform.exit();
    }

    @FXML
    private void onToggleTheme() {
        ThemeManager.toggle();
        ThemeManager.apply(startButton.getScene());
        if (browserStage != null && browserStage.isShowing()) {
            ThemeManager.apply(browserStage.getScene());
        }
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
        if (pauseButton != null) {
            pauseButton.setText("Paused");
        }
        if (settingsButton != null) {
            settingsButton.setDisable(true);
        }
        running = true;
        paused = false;
        pauseAccum = 0;
        startTime = System.currentTimeMillis();
        lastSegmentTime = 0;
        srtIndex = 1;
        if (timerLabel != null) {
            timerLabel.setText("00:00:00");
            timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateTimer()));
            timer.setCycleCount(Timeline.INDEFINITE);
            timer.play();
        }
        if (partialLabel != null) {
            partialLabel.setText("");
        }
        if (timeoutSeconds > 0) {
            autoStop = new Timeline(new KeyFrame(Duration.seconds(timeoutSeconds), e -> stopTranscription()));
            autoStop.setCycleCount(1);
            autoStop.play();
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
        if (settingsButton != null) {
            settingsButton.setDisable(false);
        }
        if (autoStop != null) {
            autoStop.stop();
            autoStop = null;
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
        File outFile = base.resolve("transcript.srt").toFile();
        LOG.fine(() -> "Writing transcript to " + outFile.getAbsolutePath());
        try (Model model = new Model(locateModelPath(modelDir).getAbsolutePath());
             BufferedWriter bw = new BufferedWriter(new FileWriter(outFile))) {
            writer = bw;
            Recognizer recognizer = new Recognizer(model, 16000.0f);
            AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            Mixer.Info selected = selectedDevice;
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
                if (settingsButton != null) {
                    settingsButton.setDisable(false);
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
                Platform.runLater(() -> partialLabel.setText(wrapDisplay(text)));
            }
        }
    }

    private void handlePartial(String json) {
        if (partialLabel == null) return;
        JSONObject obj = new JSONObject(json);
        String partial = obj.optString("partial");
        if (!partial.isEmpty()) {
            Platform.runLater(() -> partialLabel.setText(wrapDisplay(partial)));
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

    /** Return elapsed milliseconds since the session started accounting for pauses. */
    private long getElapsedMillis() {
        long now = System.currentTimeMillis();
        return now - startTime - pauseAccum - (paused ? (now - pauseStarted) : 0);
    }

    /** Format the given milliseconds in SRT timestamp format. */
    private static String formatSrtTime(long ms) {
        long h = ms / 3_600_000;
        long m = (ms % 3_600_000) / 60_000;
        long s = (ms % 60_000) / 1000;
        long milli = ms % 1000;
        return String.format("%02d:%02d:%02d,%03d", h, m, s, milli);
    }

    /**
     * Wrap the provided text so that no line exceeds {@code wrapChars} characters.
     */
    private String wrapDisplay(String text) {
        if (text.length() <= wrapChars) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + wrapChars, text.length());
            int space = text.lastIndexOf(' ', end);
            if (space > start && space < end) {
                end = space + 1;
            }
            sb.append(text, start, end).append('\n');
            start = end;
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private void writeLine(String text) {
        String[] parts = wrapDisplay(text).split("\n");
        Platform.runLater(() -> {
            for (String p : parts) {
                Label line = new Label(p);
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
            }
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
                long end = getElapsedMillis();
                writer.write(Integer.toString(srtIndex++));
                writer.newLine();
                writer.write(formatSrtTime(lastSegmentTime) + " --> " + formatSrtTime(end));
                writer.newLine();
                writer.write(text);
                writer.newLine();
                writer.newLine();
                writer.flush();
                lastSegmentTime = end;
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed writing line", e);
            }
        }
    }


    /** Return a list of available 16-bit input devices. */
    public static java.util.List<Mixer.Info> listInputDevices() {
        java.util.List<Mixer.Info> list = new java.util.ArrayList<>();
        AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mi);
            if (mixer.isLineSupported(info)) {
                list.add(mi);
            }
        }
        return list;
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

    public int getWrapChars() {
        return wrapChars;
    }

    public void setWrapChars(int wrap) {
        if (wrap > 0) {
            this.wrapChars = wrap;
        }
    }

    public Mixer.Info getSelectedDevice() {
        return selectedDevice;
    }

    public void setSelectedDevice(Mixer.Info info) {
        this.selectedDevice = info;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int secs) {
        this.timeoutSeconds = Math.max(0, secs);
    }
}
