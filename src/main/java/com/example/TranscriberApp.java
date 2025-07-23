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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TranscriberApp extends JFrame {
    private final JTextArea textArea = new JTextArea();
    private final JLabel lastWordsLabel = new JLabel("...");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final File modelDir = new File("model");
    private final File outputFile = new File("transcript.txt");
    private final JButton startStopButton = new JButton("Start");
    private volatile boolean running = false;
    private Thread recognitionThread;
    private boolean modelReady = false;

    public TranscriberApp() {
        setTitle("VOSK Transcriber");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        progressBar.setStringPainted(true);
        JPanel top = new JPanel(new BorderLayout());
        top.add(lastWordsLabel, BorderLayout.CENTER);
        top.add(progressBar, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        startStopButton.setEnabled(false);
        bottom.add(startStopButton);
        add(bottom, BorderLayout.SOUTH);

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
        if (!modelDir.exists()) {
            progressBar.setVisible(true);
            SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
                @Override
                protected Void doInBackground() throws Exception {
                    String url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";
                    Path zipPath = Paths.get("model.zip");
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
                    unzip(zipPath.toFile(), modelDir);
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
                    modelReady = true;
                    startStopButton.setEnabled(true);
                }
            };
            worker.execute();
        } else {
            modelReady = true;
            startStopButton.setEnabled(true);
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

    private void startRecognition() {
        if (!modelReady || running) {
            return;
        }
        recognitionThread = new Thread(() -> {
            try (Model model = new Model(locateModelPath(modelDir).getAbsolutePath());
                 FileWriter writer = new FileWriter(outputFile, true)) {
                Recognizer recognizer = new Recognizer(model, 16000.0f);
                AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();
                byte[] buffer = new byte[4096];
                running = true;
                SwingUtilities.invokeLater(() -> startStopButton.setText("Stop"));
                while (running && !Thread.currentThread().isInterrupted()) {
                    int n = line.read(buffer, 0, buffer.length);
                    if (n < 0) break;
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
                ex.printStackTrace();
            } finally {
                running = false;
                SwingUtilities.invokeLater(() -> startStopButton.setText("Start"));
            }
        });
        recognitionThread.start();
    }

    private void stopRecognition() {
        running = false;
        if (recognitionThread != null) {
            recognitionThread.interrupt();
        }
    }

    private void handleResult(String json, FileWriter writer) throws IOException {
        JSONObject obj = new JSONObject(json);
        String text = obj.optString("text");
        if (!text.isEmpty()) {
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
        writer.write(text + System.lineSeparator());
        writer.flush();
        SwingUtilities.invokeLater(() -> {
            textArea.append(text + System.lineSeparator());
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TranscriberApp().setVisible(true));
    }
}
