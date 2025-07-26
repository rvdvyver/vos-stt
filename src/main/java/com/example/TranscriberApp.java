package com.example;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.json.JSONObject;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.example.logging.LoggingConfig;

public class TranscriberApp extends JFrame {
    private static final Logger LOG = Logger.getLogger(TranscriberApp.class.getName());
    private final JTextArea textArea = new JTextArea();
    private final JLabel lastWordsLabel = new JLabel("...");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JProgressBar volumeBar = new JProgressBar(0, 100);
    private final JComboBox<Mixer.Info> deviceComboBox = new JComboBox<>();
    private final JComboBox<String> modelComboBox = new JComboBox<>();
    private final File modelsBaseDir = new File("models");
    private File currentModelDir;
    private final File outputFile = new File("transcript.srt");
    private final JButton startStopButton = new JButton("Start");
    private volatile boolean running = false;
    private Thread recognitionThread;
    private boolean modelReady = false;
    private long sessionStart;
    private long lastSegment;
    private int srtIndex;

    private static class ModelInfo {
        final String url;
        final String dirName;
        ModelInfo(String url, String dirName) {
            this.url = url;
            this.dirName = dirName;
        }
    }

    private final Map<String, ModelInfo> models = new LinkedHashMap<>();

    public TranscriberApp() {
        setTitle("VOSK Transcriber");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        progressBar.setStringPainted(true);
        lastWordsLabel.setFont(lastWordsLabel.getFont().deriveFont(16f));
        JPanel top = new JPanel(new BorderLayout());
        top.add(lastWordsLabel, BorderLayout.CENTER);
        top.add(progressBar, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        startStopButton.setEnabled(false);
        volumeBar.setPreferredSize(new Dimension(100, 16));
        bottom.add(volumeBar);
        bottom.add(deviceComboBox);
        bottom.add(modelComboBox);
        bottom.add(startStopButton);
        add(bottom, BorderLayout.SOUTH);

        models.put("English (small)", new ModelInfo(
                "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
                "vosk-model-small-en-us-0.15"));
        models.put("English (large)", new ModelInfo(
                "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip",
                "vosk-model-en-us-0.22"));
        for (String name : models.keySet()) {
            modelComboBox.addItem(name);
        }
        modelComboBox.addActionListener(e -> ensureModel());
        if (modelComboBox.getItemCount() > 0) {
            modelComboBox.setSelectedIndex(0);
        }

        loadInputDevices();

        startStopButton.addActionListener(e -> {
            if (!running) {
                startRecognition();
            } else {
                stopRecognition();
            }
        });

        ensureModel();
    }

    private void ensureModel() {
        String sel = (String) modelComboBox.getSelectedItem();
        if (sel == null) {
            return;
        }
        ModelInfo info = models.get(sel);
        currentModelDir = new File(modelsBaseDir, info.dirName);
        if (!isModelValid(currentModelDir)) {
            progressBar.setVisible(true);
            modelReady = false;
            updateStartButtonState();
            SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
                @Override
                protected Void doInBackground() throws Exception {
                    String url = info.url;
                    modelsBaseDir.mkdirs();
                    Path zipPath = modelsBaseDir.toPath().resolve(info.dirName + ".zip");
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
                                publish((int) (total * 100 / length));
                            }
                        }
                    }
                    unzip(zipPath.toFile(), currentModelDir);
                    Files.delete(zipPath);
                    return null;
                }

                @Override
                protected void process(List<Integer> chunks) {
                    int val = chunks.get(chunks.size() - 1);
                    progressBar.setValue(val);
                }

                @Override
                protected void done() {
                    progressBar.setVisible(false);
                    modelReady = isModelValid(currentModelDir);
                    updateStartButtonState();
                }
            };
            worker.execute();
        } else {
            modelReady = isModelValid(currentModelDir);
            updateStartButtonState();
        }
    }

    private static void unzip(File zipFile, File targetDir) throws IOException {
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

    private void loadInputDevices() {
        deviceComboBox.removeAllItems();
        AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(mi);
            if (mixer.isLineSupported(info)) {
                deviceComboBox.addItem(mi);
            }
        }
        deviceComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Mixer.Info) {
                    setText(((Mixer.Info) value).getName());
                }
                return this;
            }
        });
        if (deviceComboBox.getItemCount() > 0) {
            deviceComboBox.setSelectedIndex(0);
        }
        updateStartButtonState();
    }

    private void updateStartButtonState() {
        startStopButton.setEnabled(modelReady && deviceComboBox.getItemCount() > 0);
    }

    private void startRecognition() {
        if (!modelReady || running) {
            LOG.warning("Attempted to start recognition but model not ready or already running");
            return;
        }
        recognitionThread = new Thread(() -> {
            try (Model model = new Model(locateModelPath(currentModelDir).getAbsolutePath());
                 FileWriter writer = new FileWriter(outputFile, true)) {
                LOG.info("Recognition thread started");
                Recognizer recognizer = new Recognizer(model, 16000.0f);
                sessionStart = System.currentTimeMillis();
                lastSegment = 0;
                srtIndex = 1;
                AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                Mixer.Info selected = (Mixer.Info) deviceComboBox.getSelectedItem();
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
                running = true;
                SwingUtilities.invokeLater(() -> startStopButton.setText("Stop"));
                while (running && !Thread.currentThread().isInterrupted()) {
                    int n = line.read(buffer, 0, buffer.length);
                    if (n < 0) break;
                    final int level = calculateVolumeLevel(buffer, n);
                    SwingUtilities.invokeLater(() -> volumeBar.setValue(level));
                    if (recognizer.acceptWaveForm(buffer, n)) {
                        String result = recognizer.getResult();
                        handleResult(result, writer);
                    } else {
                        String partial = recognizer.getPartialResult();
                        handlePartial(partial);
                    }
                }
                line.stop();
                line.close();
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Recognition error", ex);
            } finally {
                running = false;
                SwingUtilities.invokeLater(() -> {
                    startStopButton.setText("Start");
                    volumeBar.setValue(0);
                });
                LOG.fine("Recognition thread finished");
            }
        });
        recognitionThread.start();
    }

    private void stopRecognition() {
        running = false;
        LOG.info("Recognition stopping");
        if (recognitionThread != null) {
            recognitionThread.interrupt();
        }
        SwingUtilities.invokeLater(() -> volumeBar.setValue(0));
    }

    private int calculateVolumeLevel(byte[] audio, int length) {
        long sum = 0;
        for (int i = 0; i < length; i += 2) {
            int sample = (audio[i + 1] << 8) | (audio[i] & 0xff);
            sum += sample * sample;
        }
        double rms = Math.sqrt(sum / (length / 2.0));
        return (int) Math.min(100, rms * 100 / 32768);
    }

    private void handleResult(String json, FileWriter writer) throws IOException {
        JSONObject obj = new JSONObject(json);
        String text = obj.optString("text");
        if (!text.isEmpty()) {
            LOG.fine(() -> "Recognised: " + text);
            appendText(text, writer);
        }
    }

    private void handlePartial(String json) {
        JSONObject obj = new JSONObject(json);
        String partial = obj.optString("partial");
        if (!partial.isEmpty()) {
            lastWordsLabel.setText(partial);
        }
    }

    private void appendText(String text, FileWriter writer) throws IOException {
        lastWordsLabel.setText(text);
        long end = System.currentTimeMillis() - sessionStart;
        writer.write(Integer.toString(srtIndex++));
        writer.write(System.lineSeparator());
        writer.write(formatSrtTime(lastSegment) + " --> " + formatSrtTime(end));
        writer.write(System.lineSeparator());
        writer.write(text);
        writer.write(System.lineSeparator());
        writer.write(System.lineSeparator());
        writer.flush();
        lastSegment = end;
        SwingUtilities.invokeLater(() -> {
            textArea.append(text + System.lineSeparator());
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    private static String formatSrtTime(long ms) {
        long h = ms / 3_600_000;
        long m = (ms % 3_600_000) / 60_000;
        long s = (ms % 60_000) / 1000;
        long milli = ms % 1000;
        return String.format("%02d:%02d:%02d,%03d", h, m, s, milli);
    }

    public static void main(String[] args) {
        LoggingConfig.configure();
        LOG.info("Launching TranscriberApp");
        SwingUtilities.invokeLater(() -> new TranscriberApp().setVisible(true));
    }
}
