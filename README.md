# CCTV Face Tracker

An Android app that discovers CP Plus DVR CCTV systems on the local network, streams their RTSP
camera feeds, and runs on-device face detection, tracking, and recognition against them — with
background operation and real-time alerts.

## Features

- **LAN discovery** — scans the active Wi-Fi subnet for DVR/NVR devices (RTSP/HTTP signature
  ports) and lists them for connection, or add a DVR manually by IP.
- **Live viewing** — plays any camera channel's RTSP sub-stream via Media3 ExoPlayer.
- **Face tracking pipeline** — for each selected camera, periodically captures a real decoded
  frame and runs it through:
  - **Detection**: YuNet (ONNX, via ONNX Runtime) for face bounding boxes + landmarks.
  - **Tracking**: greedy IoU matching to keep a stable identity for the same face across frames.
  - **Recognition**: SFace (ONNX) face embeddings compared against enrolled faces via cosine
    similarity.
- **Enrollment** — when an unrecognized face is spotted, the dashboard surfaces it with an
  "Enroll this face" action to name and save it; future sightings are then recognized by name.
- **Alerts** — Telegram photo alerts (optional bot token/chat ID) and local Android notifications
  per recognized/unknown face, both with a per-person cooldown to avoid spam.
- **Background tracking** — once started, tracking runs in a foreground service independent of
  the Activity, so it keeps running if the app is backgrounded or the screen locks. An ongoing
  notification (with a Stop action) stays visible while active.
- **Analytics & logs** — running detection counts and a timestamped activity log, persisted to
  disk and visible on the dashboard.
- **Encrypted credential storage** — saved DVR connections are encrypted with an
  AndroidKeystore-backed AES-GCM key.

## Architecture

```
app/src/main/java/com/example/cctvfacetracker/
├── MainActivity.kt            # Navigation between discovery/viewer/tracking screens
├── CctvViewer.kt               # DVR connection model + live single-camera viewer
├── TrackingScreens.kt          # Tracking setup + dashboard UI (reads from the service)
├── EnrollmentDialog.kt         # Name-and-save dialog for an unrecognized face
├── DvrCredentialStore.kt       # Encrypted (Keystore) storage for saved DVR connections
├── MainViewModel.kt            # Discovery screen state (scan results, saved connections)
│
├── pipeline/                   # Detection → tracking → recognition pipeline
│   ├── FaceDetectorImpl.kt     #   YuNet (ONNX) face detection
│   ├── FaceTrackerImpl.kt      #   Greedy IoU tracker
│   ├── FaceRecognizerImpl.kt   #   SFace (ONNX) embeddings + cosine-similarity matching
│   ├── FaceProcessor.kt        #   Wires detector → tracker → recognizer per frame
│   └── CompositeListener.kt    #   Fans pipeline callbacks out to multiple listeners
│
├── tracking/                   # Background execution
│   ├── TrackingService.kt      #   Foreground service: headless ExoPlayer + ImageReader frame
│   │                           #   capture per camera, runs the pipeline, survives backgrounding
│   ├── TrackingBus.kt          #   In-process state bridge (service → UI): live thumbnails,
│   │                           #   last unidentified face
│   └── FaceAlertNotifier.kt    #   Posts system notifications for recognized/unknown faces
│
├── alert/AlertEngine.kt        # Logs detections + sends Telegram photo alerts
├── database/                   # Persistence (JSON-file + Room)
│   ├── JsonEmbeddingRepository.kt  # Enrolled face embeddings (used by the live pipeline)
│   ├── LogRepository.kt            # Activity log (thread-safe JSON file)
│   ├── AnalyticsRepository.kt      # Detection counters (thread-safe JSON file)
│   └── Room*.kt / Face*.kt         # Room DB scaffolding (not currently wired into tracking)
└── network/
    ├── CctvNetworkScanner.kt   # Subnet TCP port scan for CCTV/DVR signatures
    └── TelegramSender.kt       # Telegram Bot API photo alert sender
```

### Why a foreground service + `ImageReader`, not a `TextureView`?

ExoPlayer has no direct "give me a bitmap" API. The natural approach — rendering into a
`TextureView` and calling `getBitmap()` — requires a live window, so it stops working the moment
the Activity is backgrounded. `TrackingService` instead decodes each camera's stream into a
headless `ImageReader` surface and converts frames to bitmaps in-process, which works with no UI
at all and is what makes background tracking possible.

## Setup

1. Open the project in Android Studio (or build with Gradle directly).
2. (Optional) To enable Telegram alerts, pass your bot token/chat ID via Gradle properties —
   e.g. in `local.properties` or `~/.gradle/gradle.properties`:
   ```properties
   telegram.botToken=123456:ABC-your-bot-token
   telegram.chatId=your-chat-id
   ```
   These are injected into `BuildConfig` at build time and never hardcoded in source. The app
   also lets you enter a token/chat ID per tracking session from the Tracking Setup screen.
3. Build and run:
   ```bash
   ./gradlew :app:installDebug
   ```
4. From the app: scan your LAN (or add a DVR manually by IP/credentials), pick a camera, and
   either view it live or start tracking.

### Permissions

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` — LAN scanning and RTSP/Telegram.
- `POST_NOTIFICATIONS` — requested at runtime (Android 13+) when tracking starts, for alert and
  status notifications.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` — required for background tracking.

## Models

Two ONNX models are bundled under `app/src/main/assets/models/`:
- `face_detection_yunet_2023mar.onnx` — YuNet face detector (OpenCV Zoo).
- `face_recognition_sface_2021dec.onnx` — SFace face recognizer (OpenCV Zoo).

Both are run on-device via ONNX Runtime; no frames or embeddings leave the device except via the
optional Telegram alert (which sends a cropped face photo, not the full frame).

## Known limitations

- Recognition accuracy depends on face pose/alignment at both enrollment and sighting time — no
  landmark-based alignment is applied before extracting embeddings yet, so matching works best
  for a reasonably frontal, well-lit face.
- The Room-based embedding storage (`database/Room*.kt`) is scaffolding left over from an earlier
  iteration and isn't wired into the live tracking pipeline, which uses
  `JsonEmbeddingRepository` instead.
