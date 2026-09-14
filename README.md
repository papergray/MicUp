<div align="center">

# 🎙️ MicUp RVC

**Real-time Okada Kazuchika Voice Conversion for Android**

[![Android](https://img.shields.io/badge/Android-8.0%2B-green?style=flat-square&logo=android)](https://github.com/asrolakimprowindows-bit/MicUp/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)
[![GPU](https://img.shields.io/badge/GPU-Kaggle%20CUDA-orange?style=flat-square)](https://www.kaggle.com)

[Download APK](#download) · [Setup Guide](#setup) · [Troubleshooting](#troubleshooting)

</div>

---

## 📖 What is MicUp RVC?

MicUp RVC is a real-time voice conversion app for Android that uses **Retrieval-based Voice Conversion (RVC)** powered by **Kaggle GPU**. It captures audio from your microphone, processes it with RVC (Okada Kazuchika voice model), and outputs the converted voice in real-time.

**Key difference from original MicUp:** This version specializes in RVC voice conversion with a dedicated UI for voice parameters, real-time monitoring, and GPU-accelerated inference via Kaggle.

### Why Kaggle GPU?
- ✅ **High quality processing** - Better than on-device due to CUDA optimization
- ✅ **HP stays smooth** - Infinix GT 20 Pro can play games while processing
- ✅ **Real-time results** - ~2-5 second latency (acceptable for voice chat)
- ✅ **No local model storage** - Cloud-based, always up-to-date

---

## ✨ Features

### 🎤 Voice Conversion
- **RVC Model Support** - Okada Kazuchika model from voice-models.com
- **F0 Methods** - RMVPE (recommended), CREPE, HARVEST, PARSELMOUTH
- **Pitch Control** - -12 to +12 semitones
- **Formant Shifting** - -3.0 to +3.0 (voice gender adjustment)
- **Index Rate** - 0.0 to 1.0 (voice search blend)
- **Speaker ID** - Support multiple voice model variants
- **Consonant Protection** - ON/OFF toggle for clarity

### 🎧 Monitoring & Control
- **Real-time Monitoring** - "Hear yourself" while recording
- **Volume Control** - 0-100% with live display
- **Connection Status** - 🟢 Connected / 🟡 Connecting / 🔴 Disconnected
- **Latency Display** - Real-time ping to Kaggle API
- **Processing Parameters** - Adjustable chunk size (1-1000), hop length (1-512)

### 💾 Advanced
- **Preset System** - Save/load RVC configurations
- **Root Mode Support** - System-wide virtual microphone (requires root/Magisk)
- **Glassmorphic UI** - iOS-inspired design with transparency & blur
- **Manual Input** - Full control over all parameters via text fields

---

## 🔧 Requirements

### Android Device
- **OS:** Android 8.0+ (API 26)
- **RAM:** 4GB minimum (8GB recommended)
- **Storage:** 100MB free space
- **Network:** Stable internet for Kaggle API
- **Permissions:** Microphone, Audio settings, Storage (optional)

### Kaggle Account
- ✅ Free Kaggle account
- ✅ GPU quota available
- ✅ RVC model files (`.pth` + `.index`)

### Optional (Enhanced)
- **Root/Magisk:** For system-wide virtual microphone
- **Shizuku:** Alternative to root (ADB-level access)

---

## 📥 Download

### Pre-built APK
[Download latest release](https://github.com/asrolakimprowindows-bit/MicUp/releases) (when available)

### Build from Source
See [Building](#building) section below.

---

## 🚀 Setup

### Part 1: Kaggle API Setup (30 minutes)

**Detailed guide:** See [`KAGGLE_STEP_BY_STEP.md`](KAGGLE_STEP_BY_STEP.md)

Quick overview:

1. **Download RVC Model**
   - Go to: https://voice-models.com/model/1qd42AajsNe
   - Download: `okada.pth` + `okada.index`

2. **Upload to Kaggle Dataset**
   ```bash
   pip install kaggle
   # Configure API key: https://www.kaggle.com/settings/account
   kaggle datasets create -p ~/path/to/okada-model
   ```

3. **Create Kaggle Notebook**
   - Go to: https://www.kaggle.com/code
   - Create new notebook with **GPU enabled**
   - Copy code from [`KAGGLE_STEP_BY_STEP.md`](KAGGLE_STEP_BY_STEP.md)
   - Run all cells
   - Share notebook as **Public**
   - Copy public URL

4. **Example Kaggle Endpoint**
   ```
   https://www.kaggle.com/api/v1/notebooks/username/rvc-inference/http
   ```

### Part 2: Android App Setup

1. **Clone Repository**
   ```bash
   git clone https://github.com/asrolakimprowindows-bit/MicUp.git
   cd MicUp
   ```

2. **Build APK**
   ```bash
   chmod +x build.sh
   ./build.sh
   ```
   Output: `MicUp-RVC.apk` in project root

3. **Install on Device**
   ```bash
   adb install -r MicUp-RVC.apk
   ```

4. **Configure in App**
   - Open MicUp → RVC tab
   - Paste Kaggle endpoint URL
   - Click "Test Connection"
   - Should show 🟢 Connected

---

## 📖 Usage

### Basic Flow

```
1. Open MicUp → RVC tab
2. Test Kaggle connection (green indicator)
3. Set voice parameters:
   - Pitch: -12 to +12
   - Formant: -3.0 to +3.0
   - F0 Method: RMVPE (default)
   - Chunk Size: 1024 (quality vs latency)
4. Toggle Monitor ON (optional, to hear yourself)
5. Adjust volume 0-100%
6. Click START to record
7. Speak into microphone
8. Converted audio plays in real-time
9. Click STOP when done
```

### Voice Parameters Explanation

| Parameter | Range | Effect | Notes |
|-----------|-------|--------|-------|
| **Pitch** | -12 to +12 | Shift voice up/down | Semitones |
| **Formant** | -3.0 to +3.0 | Voice gender/character | Negative=deeper, Positive=higher |
| **Index Rate** | 0.0 to 1.0 | How much to use voice index | 0.5 is balanced |
| **Speaker ID** | 0 to N | Which speaker variant | Depends on model |
| **F0 Method** | RMVPE/CREPE/etc | Pitch extraction | RMVPE = best quality |
| **Chunk Size** | 1-1000 | Audio chunk size | Larger = better quality, slower |
| **Protect Consonants** | ON/OFF | Preserve consonants | Keeps speech clarity |

### Monitoring (Hear Yourself)

1. Toggle "Monitor (Hear Yourself)" ON
2. Adjust volume slider 0-100%
3. While recording, converted audio plays through speaker
4. "🔊 Now playing..." indicator shows activity
5. Adjust pitch/formant live while hearing the effect

---

## 🔨 Building

### Requirements
- Android Studio 2023.1+
- Android SDK 34
- NDK 26.3+
- Java 17
- Gradle 8.2+

### Build Steps

```bash
# Clone
git clone https://github.com/asrolakimprowindows-bit/MicUp.git
cd MicUp

# Debug build (faster, not signed)
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release build (signed, production-ready)
./build.sh
# Output: MicUp-RVC.apk
```

### Build Configuration

- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)
- **CPU Architectures:** arm64-v8a, armeabi-v7a, x86_64
- **Language:** Kotlin + C++17
- **Audio Engine:** Oboe
- **UI Framework:** Jetpack Compose

---

## 🏗️ Architecture

### Tech Stack

```
┌─────────────────────────────────────────────────┐
│          MicUp RVC Android App                  │
├─────────────────────────────────────────────────┤
│ UI Layer:                                       │
│  - Jetpack Compose (glassmorphic design)       │
│  - Material3 + Custom themes                   │
├─────────────────────────────────────────────────┤
│ Business Logic:                                 │
│  - RvcConnectionViewModel (connection mgmt)    │
│  - RvcMonitorViewModel (audio playback)        │
│  - RvcSettings (parameter management)          │
├─────────────────────────────────────────────────┤
│ Audio Layer:                                    │
│  - Oboe (real-time audio I/O)                 │
│  - RvcAudioRecorder (mic capture)             │
│  - RvcAudioPlayer (audio playback)            │
│  - RvcAudioEncoder (WAV ↔ Base64)             │
├─────────────────────────────────────────────────┤
│ Network:                                        │
│  - Ktor HTTP Client (Kaggle API)              │
│  - JSON serialization (kotlinx)               │
├─────────────────────────────────────────────────┤
│ Kaggle GPU Backend:                             │
│  - Flask API server                            │
│  - RVC inference (torch)                       │
│  - Audio processing (librosa)                 │
└─────────────────────────────────────────────────┘
```

### Data Flow

```
┌──────────┐
│  Mic 🎤  │
└──────┬───┘
       │
       ▼
┌──────────────────────┐
│ RvcAudioRecorder     │
│ (capture + buffer)   │
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐
│ RvcAudioEncoder      │
│ (float → base64 WAV) │
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐      ┌─────────────┐
│ RvcApiClient         │──────│ Kaggle CUDA │
│ (HTTP POST)          │      │ (RVC GPU)   │
│                      │◄─────│ Processing  │
└──────┬───────────────┘      └─────────────┘
       │
       ▼
┌──────────────────────┐
│ RvcAudioEncoder      │
│ (base64 WAV → float) │
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐
│ RvcAudioPlayer       │
│ (if Monitor=ON)      │
└──────┬───────────────┘
       │
       ▼
┌──────────┐
│ Speaker  │ 🔊
│(or vMic) │
└──────────┘
```

### Latency Breakdown (typical)

```
Mic capture:        10-20ms
Audio encoding:     10-50ms
Network upload:     200-500ms
Kaggle processing:  1500-3000ms (GPU inference)
Network download:   200-500ms
Audio playback:     20-50ms
───────────────────────────
Total latency:      ~2-4 seconds
```

---

## 🐛 Troubleshooting

### Connection Issues

**Problem:** Red indicator "Disconnected"

**Solutions:**
- Check internet connection
- Verify Kaggle endpoint URL is correct
- Make sure Kaggle notebook is **Public** (Share → Public)
- Open notebook → Click "Run All" to restart server
- Wait 30 seconds for server to initialize

**Problem:** "403 Forbidden" or "404 Not Found"

**Solutions:**
- Notebook must be **Public** (not Private)
- Double-check notebook URL spelling
- Make sure you copied the right URL from Share link

### Audio Issues

**Problem:** No sound from speaker when Monitor=ON

**Solutions:**
- Check device volume is not muted
- Check app has microphone permission (Settings → Apps → MicUp → Permissions)
- Make sure Monitor toggle is ON (blue)
- Verify volume slider is not at 0%
- Test audio playback with other app first

**Problem:** Microphone not capturing audio

**Solutions:**
- Grant microphone permission when prompted
- Check phone settings: Settings → Apps → MicUp → Permissions → Microphone
- Test mic with Google Recorder or voice app
- Try different recording app

### Performance Issues

**Problem:** High latency (>5 seconds)

**Solutions:**
- Reduce chunk size (try 512 instead of 1024)
- Check Kaggle GPU quota (Kaggle notebook Settings)
- Close other apps running on phone
- Reduce formant/pitch calculations (simpler = faster)
- Check internet speed (need ≥5 Mbps)

**Problem:** App crashes on startup

**Solutions:**
- Clear app cache: Settings → Apps → MicUp → Storage → Clear Cache
- Reinstall APK: `adb uninstall com.micplugin && adb install MicUp-RVC.apk`
- Check Android version ≥8.0
- Check device RAM ≥4GB available

### Kaggle Issues

**Problem:** Model not loading in Kaggle notebook

**Solutions:**
- Verify dataset is mounted correctly
- Check model file paths in notebook code
- Ensure model files uploaded completely (check file size)
- Try re-uploading model files to dataset

**Problem:** GPU not detected in Kaggle

**Solutions:**
- Notebook Settings → Accelerator: change to **GPU**
- Verify GPU quota available (Kaggle Account → Accelerator)
- Restart notebook (⚙️ → Restart kernel)

---

## 📚 Documentation

- **[KAGGLE_STEP_BY_STEP.md](KAGGLE_STEP_BY_STEP.md)** - Detailed Kaggle setup (Indonesian)
- **[KAGGLE_SETUP.md](KAGGLE_SETUP.md)** - Quick Kaggle reference
- **[kaggle_rvc_notebook.py](kaggle_rvc_notebook.py)** - Python notebook template
- **[build.sh](build.sh)** - Build & signing script

---

## 🔐 Privacy & Permissions

### Permissions Requested
- `RECORD_AUDIO` - Microphone capture
- `MODIFY_AUDIO_SETTINGS` - Audio routing
- `POST_NOTIFICATIONS` - (Android 13+) Status notifications
- `READ_EXTERNAL_STORAGE` - (Android 12-) File access

### Data Handling
- Audio is sent to **your own Kaggle notebook** (not third-party)
- Audio is **not stored** on Kaggle (processed & discarded)
- No analytics or tracking
- Open source - audit the code yourself

---

## 📄 License

MIT License - See [LICENSE](LICENSE) file

---

## 🤝 Contributing

Contributions welcome! Please:

1. Fork the repository
2. Create feature branch: `git checkout -b feature/your-feature`
3. Commit changes: `git commit -am 'Add feature'`
4. Push to branch: `git push origin feature/your-feature`
5. Create Pull Request

---

## 📞 Support

- **Issues:** [GitHub Issues](https://github.com/asrolakimprowindows-bit/MicUp/issues)
- **Discussions:** [GitHub Discussions](https://github.com/asrolakimprowindows-bit/MicUp/discussions)
- **Documentation:** Check `.md` files in repo

---

<div align="center">

**Made with ❤️ for real-time voice conversion**

Built with Kotlin • Powered by Kaggle GPU • Inspired by MicUp

</div>
