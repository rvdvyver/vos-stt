# vos-stt

This project now provides a minimal JavaFX application called **vos-tts** that
shows live speech transcription in a compact window. The transcription is
generated using a mocked service for demonstration purposes and appended to a
session file.

## Usage

Launch the JavaFX UI with:

```bash
mvn compile exec:java
```

Click **Start Live Transcription** to begin a session. Lines of text will
appear in large font as the mock recogniser generates them. A file named after
the session timestamp is written to disk.

Use **🗂 Browse Sessions** to open the new Transcription Browser. From there you
can open previous transcripts in a modal viewer or remove old sessions.
