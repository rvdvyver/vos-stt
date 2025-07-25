package com.example.vostts;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Model representing a stored transcription session. */
public class SessionMetadata {
    private final String name;
    private final String date;
    private final String duration;
    private final String status;
    private final Path directory;

    public SessionMetadata(String name, String date, String duration, String status, Path directory) {
        this.name = name;
        this.date = date;
        this.duration = duration;
        this.status = status;
        this.directory = directory;
    }

    public String getName() {
        return name;
    }

    public String getDate() {
        return date;
    }

    public String getDuration() {
        return duration;
    }

    public String getStatus() {
        return status;
    }

    public Path getDirectory() {
        return directory;
    }

    /**
     * Load session metadata from the given session directory.
     */
    public static SessionMetadata load(Path dir) throws IOException {
        Path metaFile = dir.resolve("metadata.json");
        String name = dir.getFileName().toString();
        String date = "";
        String duration = "";
        String status = "";
        if (Files.exists(metaFile)) {
            String json = Files.readString(metaFile);
            JSONObject obj = new JSONObject(json);
            name = obj.optString("name", name);
            date = obj.optString("date", date);
            duration = obj.optString("duration", duration);
            status = obj.optString("status", status);
        }
        // fallback formatting if date missing
        if (date.isEmpty()) {
            LocalDateTime time = LocalDateTime.ofInstant(Files.getLastModifiedTime(dir).toInstant(), java.time.ZoneId.systemDefault());
            date = time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
        return new SessionMetadata(name, date, duration, status, dir);
    }
}
