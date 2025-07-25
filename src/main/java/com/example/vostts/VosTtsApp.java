package com.example.vostts;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.stage.Stage;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.concurrent.Task;

import com.example.logging.LoggingConfig;

import java.util.logging.Logger;

import java.io.File;

public class VosTtsApp extends Application {
    private static final Logger LOG = Logger.getLogger(VosTtsApp.class.getName());

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/vostts/app.fxml"));
        Parent root = loader.load();
        VosTtsController controller = loader.getController();

        File modelDir = new File("models/vosk-model-en-us-0.22");
        controller.setModelDir(modelDir);
        LOG.fine(() -> "Using model directory: " + modelDir.getAbsolutePath());

        if (VosTtsController.isModelValid(modelDir)) {
            LOG.info("Speech model found");
            controller.setModelReady(true);
            Scene scene = new Scene(root, 400, 300);
            scene.getStylesheets().add(getClass().getResource("/com/example/vostts/dark.css").toExternalForm());
            stage.setTitle("vos-tts");
            stage.setScene(scene);
            stage.show();
        } else {
            LOG.info("Speech model not present, downloading...");
            ProgressBar bar = new ProgressBar(0);
            Label label = new Label("Downloading speech model...\nThis may take a few minutes.");
            VBox box = new VBox(10, label, bar);
            box.setStyle("-fx-padding: 20; -fx-alignment: center;");
            Scene splashScene = new Scene(box, 400, 150);
            stage.setScene(splashScene);
            stage.setTitle("Preparing Model");
            stage.show();

            Task<Void> task = controller.createModelDownloadTask(modelDir);
            bar.progressProperty().bind(task.progressProperty());
            task.setOnSucceeded(e -> {
                controller.setModelReady(true);
                LOG.info("Speech model ready");
                Scene scene = new Scene(root, 400, 300);
                scene.getStylesheets().add(getClass().getResource("/com/example/vostts/dark.css").toExternalForm());
                stage.setScene(scene);
            });
            new Thread(task).start();
        }
    }

    public static void main(String[] args) {
        LoggingConfig.configure();
        LOG.info("Launching application");
        launch(args);
    }
}
