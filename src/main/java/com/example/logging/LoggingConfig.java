package com.example.logging;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Utility to configure application logging. */
public final class LoggingConfig {
    private LoggingConfig() {}

    /**
     * Configure the root logger. The level can be controlled via the
     * {@code log.level} system property. Valid values correspond to
     * {@link java.util.logging.Level} names. Defaults to {@code INFO}.
     */
    public static void configure() {
        Level level = Level.INFO;
        String prop = System.getProperty("log.level");
        if (prop != null && !prop.isEmpty()) {
            try {
                level = Level.parse(prop.toUpperCase());
            } catch (IllegalArgumentException ex) {
                // fall back to INFO if parsing fails
            }
        }
        Logger root = Logger.getLogger("");
        root.setLevel(level);
        for (var h : root.getHandlers()) {
            h.setLevel(level);
        }
        // Ensure a simple console formatter is used
        if (root.getHandlers().length == 0) {
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(level);
            handler.setFormatter(new SimpleFormatter());
            root.addHandler(handler);
        }
    }
}
