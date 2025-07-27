package com.example.vostts;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.stage.Stage;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.StageStyle;
import javafx.scene.layout.VBox;
import javafx.concurrent.Task;

// Application utilities
import com.example.vostts.ThemeManager;
import com.example.vostts.DragUtil;

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

        stage.initStyle(StageStyle.UNDECORATED);

        // Splash screen while loading
        FXMLLoader splashLoader = new FXMLLoader(getClass().getResource("/com/example/vostts/splash.fxml"));
        Parent splashRoot = splashLoader.load();
        ProgressBar bar = (ProgressBar) splashLoader.getNamespace().get("bar");
        Label statusLabel = (Label) splashLoader.getNamespace().get("statusLabel");
        Scene splashScene = new Scene(splashRoot, 400, 170);
        ThemeManager.apply(splashScene);
        Stage splashStage = new Stage(StageStyle.UNDECORATED);
        splashStage.setTitle("Preparing Model");
        splashStage.setScene(splashScene);
        DragUtil.makeDraggable(splashStage, splashRoot);
        splashStage.show();

        Task<Void> task;
        if (VosTtsController.isModelValid(modelDir)) {
            LOG.info("Speech model found");
            controller.setModelReady(true);
            task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    updateProgress(1,1);
                    Thread.sleep(2000);
                    return null;
                }
            };
        } else {
            LOG.info("Speech model not present, downloading...");
            statusLabel.setText("Downloading speech model...\nThis may take a few minutes.");
            task = controller.createModelDownloadTask(modelDir);
            bar.progressProperty().bind(task.progressProperty());
            statusLabel.textProperty().bind(task.messageProperty());
        }

        task.setOnSucceeded(e -> {
            controller.setModelDir(modelDir);
            controller.setModelReady(true);
            LOG.info("Speech model ready");
            Scene scene = new Scene(root, 400, 300);
            ThemeManager.apply(scene);
            DragUtil.makeDraggable(stage, root);
            splashStage.close();
            stage.setScene(scene);
            stage.show();
        });

        new Thread(task).start();
    }

    public static void main(String[] args) {
        LoggingConfig.configure();
        LOG.info("Launching application");
        launch(args);
    }
}
