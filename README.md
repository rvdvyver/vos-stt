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
appear in large font as the mock recogniser generates them. While a session is
running the subtitle file `transcript.srt` is written to the application
directory. Once the session ends the file is moved into a session folder under
your home directory with timestamps for every recognised phrase.

Use **🗂 Browse Sessions** to open the new Transcription Browser. From there you
can open previous transcripts in a modal viewer or remove old sessions.

The settings menu now includes an option to control how many characters are
displayed on a single transcription line before wrapping occurs. The default is
35 characters.

## Packaging for macOS

The project includes a Maven configuration using the
`jpackage-maven-plugin` to build a macOS DMG installer.
Run the tests first to generate the application icon and then invoke
the plugin:

```bash
mvn test package
```

The resulting `vos-stt.dmg` can be found in the `target` directory.
