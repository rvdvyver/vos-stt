package com.example.vostts;

import javafx.scene.Scene;

/** Utility class to manage global UI theme. */
public final class ThemeManager {
    private static boolean darkMode = true;

    private ThemeManager() {}

    /** Return true if the dark theme is active. */
    public static boolean isDarkMode() {
        return darkMode;
    }

    /** Toggle between dark and light themes. */
    public static void toggle() {
        darkMode = !darkMode;
    }

    /** Apply the current theme stylesheets to the given scene. */
    public static void apply(Scene scene) {
        if (scene == null) return;
        String dark = ThemeManager.class.getResource("/com/example/vostts/dark.css").toExternalForm();
        String light = ThemeManager.class.getResource("/com/example/vostts/light.css").toExternalForm();
        scene.getStylesheets().removeAll(dark, light);
        scene.getStylesheets().add(darkMode ? dark : light);
    }
}
