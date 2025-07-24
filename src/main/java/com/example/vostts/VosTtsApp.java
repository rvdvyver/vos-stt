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

import java.io.File;

public class VosTtsApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/vostts/app.fxml"));
        Parent root = loader.load();
        VosTtsController controller = loader.getController();

        File modelDir = new File("models/vosk-model-en-us-0.22");
        controller.setModelDir(modelDir);

        if (VosTtsController.isModelValid(modelDir)) {
            controller.setModelReady(true);
            Scene scene = new Scene(root, 400, 300);
            scene.getStylesheets().add(getClass().getResource("/com/example/vostts/light.css").toExternalForm());
            stage.setTitle("vos-tts");
            stage.setScene(scene);
            stage.show();
        } else {
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
                Scene scene = new Scene(root, 400, 300);
                scene.getStylesheets().add(getClass().getResource("/com/example/vostts/light.css").toExternalForm());
                stage.setScene(scene);
            });
            new Thread(task).start();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
