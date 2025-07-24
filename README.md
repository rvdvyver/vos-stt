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

When launched, the UI shows a drop down of a few demo models. The selected
model is stored under the `models` directory. If the chosen model is not
present it is downloaded automatically and extracted. A progress bar indicates
the download state.
