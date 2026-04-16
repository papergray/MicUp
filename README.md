<div align="center">

# 🎙️ MicUp

**Real-time microphone audio processing for Android**

[![Release](https://img.shields.io/github/v/release/papergray/MicUp?style=flat-square&color=blue)](https://github.com/papergray/MicUp/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green?style=flat-square&logo=android)](https://github.com/papergray/MicUp/releases/latest)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

[Download APK](https://github.com/papergray/MicUp/releases/latest) · [Report Bug](https://github.com/papergray/MicUp/issues)

</div>

---

## What is MicUp?

MicUp is a real-time microphone processing app for Android. It captures your mic input, runs it through a DSP effects chain, and routes the processed audio to a virtual microphone — so every app on your phone (Discord, Teams, Zoom, WhatsApp, etc.) hears the cleaned-up, processed version of your voice.

No PC required. No monthly subscription.

---

## Features

-  **Built-in DSP chain** — Noise Gate, 10-band EQ, Compressor, Reverb, Pitch Shifter
-  **Plugin support** — Load LV2, CLAP, and VST3 native plugins (`.so`, `.clap`, `.lv2`)
-  **Open plugin files** — Tap a plugin file in your file manager to import it directly
-  **Monitor toggle** — Hear your processed audio through headphones, or silence it while still routing to other apps
-  **Shizuku support** — ADB-level ALSA loopback routing without full root
-  **Three virtual mic tiers** — VoIP stream (no root), Shizuku (ADB), or Magisk (full root)
-  **Live VU meters** — Input, output, and gain reduction metering
-  **Preset system** — Save and load your effect configurations
-  **Built-in crash reporter** — Crash logs auto-shared for easy bug reporting

---

## Download

Grab the latest signed APK from the [Releases page](https://github.com/papergray/MicUp/releases/latest).

No Play Store. No sign-in. Just install and go.

> **Enable "Install from unknown sources"** in Android Settings → Apps → Special app access before installing.

---

## How to Use

1. Install the APK
2. Grant microphone permission when prompted
3. Tap the **power button** to start audio processing
4. Adjust effects with the sliders and knobs
5. In your call/meeting app, select **MicUp** or the VoIP audio stream as your microphone

### Loading Plugins

- Open your file manager, navigate to your plugin `.so` / `.clap` / `.lv2` file
- Tap it → select **MicUp** → plugin is imported and added automatically
- Or go to **Settings → Manage Plugin Paths** to add a folder to scan

### Shizuku (optional, no root needed)

Shizuku gives MicUp ADB-level access for better audio routing:

1. Install [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) from Play Store
2. Start Shizuku via wireless debugging (Android 11+)
3. Open MicUp → Settings → Shizuku → tap **Grant**

---

## Building from Source

**Requirements:** Android Studio, NDK 26, CMake 3.22, JDK 17

```bash
git clone https://github.com/papergray/MicUp.git
cd MicUp
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Supported Formats

| Format | Extension | Notes |
|--------|-----------|-------|
| LV2 | `.lv2` `.so` | Requires `liblilv` |
| CLAP | `.clap` `.so` | CLAP 1.2.1 |
| VST3 | `.so` | Experimental |
| APK Plugin | installed app | Via AIDL interface |

---

## Requirements

- Android 8.0+ (API 26)
- ARM64, ARMv7, or x86_64
- Microphone permission
- For Shizuku tier: Android 11+
- For Root tier: Magisk

---

## License

MIT License — see [LICENSE](LICENSE)

---

<div align="center">
Made for Android · Built with Oboe, C++17, Jetpack Compose
</div>
