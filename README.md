# Audio Visualizer

Real-time Android audio visualizer built with **Jetpack Compose**, **Clean Architecture**, **Hilt**, and **AGSL (`RuntimeShader`)** effects.

## Features

- **Real-Time Global Audio Telemetry (`40 Hz`)**
  - Synchronized FFT frequency spectrum & PCM waveform capture via Android `Visualizer` + `MEASUREMENT_MODE_PEAK_RMS` + volume-curve calibration (`AudioManager.getStreamVolumeDb`).
  - **7-Band Frequency Breakdown**: Sub-Bass, Bass, Low-Mid, Mid, High-Mid, Treble, and Brilliance.
  - **Rhythm & Spectral Dynamics**: Beat pulse, overall energy, spectral flux, estimated BPM, rhythm onset density, and spectral centroid brightness.
  - **Waveform & Loudness**: Time-domain RMS, peak, zero-crossing rate, crest factor, and calibrated source dBFS.
- **Unified Animation Parameter Engine (`AudioAnimationParams`)**
  - Fuses all extracted audio metrics into `MotionMood` (`CALM`, `GROOVE`, `ENERGETIC`, `DROP`), speed multipliers, asymmetric scale-in/scale-out timings, ripple drive, and glowing border dynamics.
- **Album Showcase Card with Selectable AGSL & Rhythm Effects**
  - Ambient primary/secondary palette extracted directly from active media session artwork.
  - Inline effect selector inside the top card:
    - **Off** — clean static card with animations disabled
    - **Ripple** *(AGSL)* — beat-reactive concentric harmonic wave refraction
    - **Water Drop** *(AGSL)* — multi-droplet liquid surface splash, Snell refraction & specular caustics (beats + tap-to-splash)
    - **Reverse Dust** *(AGSL)* — Thanos-snap-reversed particle coalescence from nothing into the card shape
    - **Border Glow** — JBL-speaker-style multi-pass glowing perimeter snake synced to audio rhythm
- **3-Tab Bottom Navigation**
  - **FFT**: 48-band logarithmic spectrum (`45 Hz – 14 kHz`) with peak-hold caps and 4 live summary tiles.
  - **Waveform**: Real-time 128-point PCM oscilloscope with RMS & peak envelope.
  - **Metrics**: Full live breakdown showing raw measurements alongside normalized `0.00 – 1.00` values and percentages (`0% – 100%`).
- **Runtime Permissions**
  - Requests `RECORD_AUDIO` at runtime and provides a one-tap Notification Access shortcut for album art & ambient colors.

## Architecture

- `:domain` — Pure Kotlin JVM models (`AudioTelemetry`, `AudioAnimationParams`, `CardVisualEffect`, `NowPlayingTrack`), signal analyzer (`AudioSignalAnalyzer`), animation parameter computer (`AudioAnimationParamsComputer`), repository interfaces, and use cases.
- `:data` — `AudioVisualizerRepositoryImpl` (dedicated `HandlerThread`), `NowPlayingRepositoryImpl` (`MediaSessionManager` + `VisualizerNotificationListenerService`), `MediaArtworkColorExtractor`, and Hilt bindings.
- `:presentation` — Jetpack Compose Material 3 UI, `VisualizerViewModel`, AGSL shaders (`AlbumCardAgslShaders`), and modular UI components.
- `:app` — Application entry point, runtime permission flow, and foreground lifecycle capture wiring.

## Requirements & Build

- **Min SDK**: Android 13 (API 33) — required for native AGSL `RuntimeShader`
- **Target / Compile SDK**: API 37 (`37.2`)
- **JDK**: Java 21

```bash
./gradlew :domain:test lintDebug assembleDebug
```
