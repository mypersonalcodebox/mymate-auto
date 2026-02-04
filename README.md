# MyMate 🤖

**Your AI companion for Android Auto and beyond**

MyMate brings the power of AI assistance to your daily life — whether you're driving, working, or just need a helpful companion. Chat naturally, save memories, set reminders, and let MyMate remember where you parked.

[![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## ✨ Features

### 💬 Natural Conversations
Chat with your AI assistant using text or voice. MyMate understands context and provides helpful, thoughtful responses.

### 🚗 Android Auto Integration
Use MyMate hands-free while driving. Voice input allows you to have conversations, ask questions, and get information without taking your eyes off the road.

### 🅿️ Smart Parking
Never forget where you parked again! MyMate automatically saves your parking location when you disconnect from Android Auto, or you can save it manually with a single tap.

### 🧠 Memories
Save important information, notes, and context that MyMate will remember across conversations. Your memories are stored locally and synced with your personal AI gateway.

### ⏰ Reminders
Set reminders using natural language. Just tell MyMate what you need to remember and when, and it'll make sure you don't forget.

### 🔒 Privacy-First
Your data stays yours. MyMate connects to your own OpenClaw Gateway — no third-party servers, no data harvesting, just you and your AI.

---

## 📱 Screenshots

*Screenshots coming soon*

| Home | Chat | Android Auto | Parking |
|:----:|:----:|:------------:|:-------:|
| ![Home](docs/screenshots/home.png) | ![Chat](docs/screenshots/chat.png) | ![Auto](docs/screenshots/auto.png) | ![Parking](docs/screenshots/parking.png) |

---

## 📋 Requirements

- **Android 8.0** (Oreo, API 26) or higher
- **OpenClaw Gateway** running and accessible
- **Network connection** to reach your gateway (WiFi or mobile data)
- **Android Auto** (optional) for in-car usage

---

## 🚀 Quick Start

### 1. Install MyMate
Download and install the APK on your Android device.

### 2. Configure Connection
Open MyMate and go to **Settings**. Enter your OpenClaw Gateway details:
- **Gateway URL**: Your gateway address (e.g., `http://100.124.24.27:18789`)
- **Auth Token**: Your gateway authentication token

### 3. Test Connection
Tap **Test Connection** to verify everything works. You should see a success message.

### 4. Start Chatting!
Return to the home screen and start a conversation with your AI companion.

### 5. Connect Android Auto (Optional)
Plug your phone into your car or connect wirelessly. MyMate will appear in Android Auto under the IoT category.

---

## 📖 Documentation

- **[Setup Guide](docs/SETUP.md)** — Detailed installation and configuration instructions
- **[Privacy Policy](docs/PRIVACY_POLICY.md)** — How MyMate handles your data

---

## 🏗️ Building from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/mymate-auto.git
cd mymate-auto

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease
```

The APK will be in `app/build/outputs/apk/`.

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 💖 Acknowledgments

- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Android Auto support via [Car App Library](https://developer.android.com/training/cars)
- Powered by [OpenClaw](https://github.com/openclaw)

---

<p align="center">
  <b>MyMate</b> — Your AI, your way 🚀
</p>
