# LiveStream Pro — Native Android Live HLS Streaming App

A high-performance **native Android live streaming app** built in 100% Kotlin with **AndroidX Media3 (ExoPlayer)** and an **automated background `.m3u8` stream extractor engine** (referenced from `mediaurl`).

---

## ⚡ Key Highlights

- **Background Stream Extraction Engine**:
  - Automatically loads embed web player URLs (e.g. `http://playsza.zu/uembed.php?id=star4b`) in an off-screen headless WebView.
  - Intercepts network requests (`shouldInterceptRequest`) for `.m3u8`, `/hls/`, `.mpd`, `playlist.m3u8`, `chunklist`, and signed stream tokens.
  - Injects deep JavaScript sniffers hooking `XMLHttpRequest`, `window.fetch`, `HTMLMediaElement.prototype.src`, `Hls.prototype.loadSource`, and `Clappr.Player.prototype.load`.
  - Scans DOM and inline script tags for regex `.m3u8` matches and de-obfuscates string array joins.
  - Auto-clicks Cloudflare Turnstile verification checkboxes / iframes.
  - Auto-plays video elements to force player manifests to load.
  - Captures exact CDN headers (`Referer`, `User-Agent`, `Origin`).

- **Instant Live Playback**:
  - As soon as the user taps a channel, the app extracts the m3u8 stream in the background and immediately starts playback on **AndroidX Media3 ExoPlayer** with low-latency buffering.
  - **Cache & Pre-Warming**: Pre-extracts the primary channel on launch so playback is instant (0-second wait).
  - **Auto-Recovery**: If a stream token expires or connection drops, the player invalidates the cache, automatically re-extracts a fresh m3u8 URL, and resumes playback seamlessly.

- **Player Features**:
  - Fullscreen landscape mode with auto-hiding controls and immersive system UI.
  - Aspect Ratio Cycler: **Fit to Screen**, **Fill Screen**, and **Zoom / Crop**.
  - Stream Inspector Dialog: View and copy the live extracted `.m3u8` URL and `Referer` header with 1 tap.
  - Re-extract / Refresh button.

- **GitHub Secrets Configuration**:
  - Easily configure all 6 channels via GitHub Secrets in your repository settings or via `local.properties`.
  - Also includes an in-app **⚙️ Edit URLs** sheet to modify and test channels on-device.

---

## 📺 Configured Channels

| Channel | Default Name | Default Embed URL |
|---|---|---|
| **Channel 1 (Target)** | `Star Sports 1 HD` | `http://playsza.zu/uembed.php?id=star4b` |
| **Channel 2** | `Star Sports 1 Hindi HD` | `http://playsza.zu/uembed.php?id=starhindi` |
| **Channel 3** | `Star Sports 2 HD` | `http://playsza.zu/uembed.php?id=star2` |
| **Channel 4** | `Sony Sports Ten 1 HD` | `http://playsza.zu/uembed.php?id=sony1` |
| **Channel 5** | `Willow Cricket HD` | `http://playsza.zu/uembed.php?id=willow` |
| **Channel 6** | `DD Sports 1.0 HD` | `http://playsza.zu/uembed.php?id=ddsports` |

---

## 🔐 GitHub Secrets Setup

In your GitHub repository, go to **Settings** → **Secrets and variables** → **Actions** → **New repository secret**:

| Secret Name | Example Value | Description |
|---|---|---|
| `CHANNEL_1_NAME` | `Star Sports 1 HD` | Display name for Channel 1 |
| `CHANNEL_1_URL` | `http://playsza.zu/uembed.php?id=star4b` | Embed URL for Channel 1 |
| `CHANNEL_2_NAME` | `Star Sports 1 Hindi HD` | Display name for Channel 2 |
| `CHANNEL_2_URL` | `http://playsza.zu/uembed.php?id=starhindi` | Embed URL for Channel 2 |
| `CHANNEL_3_NAME` | `Star Sports 2 HD` | Display name for Channel 3 |
| `CHANNEL_3_URL` | `http://playsza.zu/uembed.php?id=star2` | Embed URL for Channel 3 |
| `CHANNEL_4_NAME` | `Sony Sports Ten 1 HD` | Display name for Channel 4 |
| `CHANNEL_4_URL` | `http://playsza.zu/uembed.php?id=sony1` | Embed URL for Channel 4 |
| `CHANNEL_5_NAME` | `Willow Cricket HD` | Display name for Channel 5 |
| `CHANNEL_5_URL` | `http://playsza.zu/uembed.php?id=willow` | Embed URL for Channel 5 |
| `CHANNEL_6_NAME` | `DD Sports 1.0 HD` | Display name for Channel 6 |
| `CHANNEL_6_URL` | `http://playsza.zu/uembed.php?id=ddsports` | Embed URL for Channel 6 |

*(Optional)* You can also supply a single JSON string secret named `CHANNELS_JSON` with a custom list of any number of channels:
```json
[
  {"id":"star4b", "name":"Star Sports 1 HD", "embedUrl":"http://playsza.zu/uembed.php?id=star4b", "category":"Cricket Live"},
  {"id":"starhindi", "name":"Star Sports Hindi", "embedUrl":"http://playsza.zu/uembed.php?id=starhindi", "category":"Hindi Live"}
]
```

---

## 🛠️ Building the APK

### 1. Via GitHub Actions (Recommended)
1. Push your code to GitHub or navigate to **Actions** → **Build LiveStreamApp APK** → **Run workflow**.
2. GitHub Actions will read your secrets and compile both Debug and Release APKs.
3. Download the built APK directly from the workflow run artifacts or GitHub Releases!

### 2. Local Build
```bash
./gradlew assembleDebug
```
The APK will be located at: `app/build/outputs/apk/debug/app-debug.apk`.
