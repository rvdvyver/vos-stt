# vos-stt

This project demonstrates a simple Java Swing application that performs live
speech transcription using the [VOSK](https://alphacephei.com/vosk/) library.
The application listens to the default system audio device and appends
recognized speech to `transcript.txt`. While running it shows the current
partial transcription and a small volume bar so you can see that the microphone
is active.

## Usage

The project is built with Maven. To run the UI:

```bash
mvn compile exec:java
```

On startup the program checks for a speech model inside the `model` directory.
If the model is missing it attempts to download
`vosk-model-small-en-us-0.15.zip` and extract it. The archive contains a
subdirectory with the actual model files which the application now detects
automatically. A progress bar is shown during the download.
