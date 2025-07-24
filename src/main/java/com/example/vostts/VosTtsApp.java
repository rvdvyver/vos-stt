package com.example.vostts;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.stage.Stage;

public class VosTtsApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/vostts/app.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 400, 300);
        scene.getStylesheets().add(getClass().getResource("/com/example/vostts/light.css").toExternalForm());
        stage.setTitle("vos-tts");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
