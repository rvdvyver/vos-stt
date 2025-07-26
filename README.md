# vos-stt

This project now provides a minimal JavaFX application called **vos-tts** that
shows live speech transcription in a compact window. The transcription is
generated using a mocked service for demonstration purposes and appended to a
session file.

## Usage

Launch the JavaFX UI with:

```bash
mvn javafx:run
```

Using the JavaFX plugin avoids warnings about an unsupported configuration when
running the application.

Click **Start Live Transcription** to begin a session. Lines of text will
appear in large font as the mock recogniser generates them. Each session now
writes a `transcript.srt` subtitle file with timestamps for every recognised
phrase.

Use **🗂 Browse Sessions** to open the new Transcription Browser. From there you
can open previous transcripts in a modal viewer or remove old sessions.

The settings menu now includes an option to control how many characters are
displayed on a single transcription line before wrapping occurs. The default is
35 characters.
