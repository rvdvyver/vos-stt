package com.example.vostts;

import javafx.scene.Node;
import javafx.stage.Stage;

/** Simple helper to make undecorated stages draggable. */
public final class DragUtil {
    private DragUtil() {}

    /**
     * Make the specified node act as a drag handle for moving the stage.
     */
    public static void makeDraggable(Stage stage, Node node) {
        final double[] offset = new double[2];
        node.setOnMousePressed(e -> {
            offset[0] = e.getSceneX();
            offset[1] = e.getSceneY();
        });
        node.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - offset[0]);
            stage.setY(e.getScreenY() - offset[1]);
        });
    }
}
