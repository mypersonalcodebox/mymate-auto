# MyMate Setup Guide

This guide will walk you through setting up MyMate on your Android device.

---

## Prerequisites

Before you begin, make sure you have:

1. **Android device** running Android 8.0 (Oreo) or higher
2. **OpenClaw Gateway** installed and running on your server or home network
3. **Network access** from your phone to the gateway (local network, VPN, or Tailscale)

---

## Step 1: Install the App

### Option A: Download APK
1. Download the latest MyMate APK from the releases page
2. Enable "Install from unknown sources" if prompted
3. Open the APK file and tap **Install**

### Option B: Build from Source
```bash
git clone https://github.com/yourusername/mymate-auto.git
cd mymate-auto
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Step 2: Configure Your Gateway

### Get Your Gateway Details

You'll need two pieces of information from your OpenClaw Gateway:

1. **Gateway URL** — The address where your gateway is running
   - Local network: `http://192.168.1.100:18789`
   - Tailscale: `http://100.x.x.x:18789`
   
2. **Auth Token** — Your gateway authentication token
   - Find it in `~/.openclaw/openclaw.json` under `gateway.auth.token`

### Configure in MyMate

1. Open MyMate on your phone
2. Tap the **Settings** icon (gear) in the top right
3. Enter your **Gateway URL**
4. Enter your **Auth Token**
5. Tap **Test Connection**

If successful, you'll see a green checkmark. If not, check:
- Is your gateway running? (`openclaw gateway status`)
- Can your phone reach the gateway IP?
- Is the port correct (default: 18789)?
- Is the auth token correct?

---

## Step 3: Grant Permissions

MyMate may request the following permissions:

| Permission | Why It's Needed |
|------------|-----------------|
| **Microphone** | Voice input for chat and Android Auto |
| **Location** | Parking location feature |
| **Notifications** | Reminders and important alerts |
| **Background location** | Auto-save parking when disconnecting |

Grant permissions as needed. All features work without background location, but auto-parking won't save automatically.

---

## Step 4: Android Auto Setup (Optional)

### Enable Developer Mode in Android Auto

To use MyMate with Android Auto:

1. Open **Android Auto** settings on your phone
2. Scroll to the bottom and tap **Version** 10 times
3. A toast will confirm "Developer mode enabled"
4. Tap the three-dot menu → **Developer settings**
5. Enable **Unknown sources**

### Connect to Your Car

1. Plug your phone into your car via USB (or connect wirelessly)
2. Android Auto will launch
3. MyMate appears in the app launcher under the **IoT** category
4. Tap to launch and start chatting hands-free!

---

## Step 5: Verify Everything Works

### Test Chat
1. Open MyMate
2. Type or say "Hello!"
3. You should receive a response from your AI

### Test Parking (Optional)
1. Go to Settings → Parking
2. Tap **Save Current Location**
3. Verify the location is saved

### Test Android Auto (Optional)
1. Connect to Android Auto
2. Launch MyMate from the app list
3. Use the microphone to send a message
4. Verify the AI responds

---

## Troubleshooting

### "Connection failed"
- Verify gateway URL is correct (include `http://` or `https://`)
- Check that your phone can reach the gateway IP (try in browser)
- Verify the auth token matches your gateway config
- Ensure the gateway is running: `openclaw gateway status`

### "Voice not working"
- Grant microphone permission in Android settings
- Check that no other app is using the microphone
- Try restarting the app

### "Android Auto doesn't show MyMate"
- Enable unknown sources in Android Auto developer settings
- Make sure developer mode is enabled
- Try disconnecting and reconnecting to your car
- Clear Android Auto cache and restart

### "Parking location not accurate"
- Enable high-accuracy location mode in Android settings
- Grant background location permission for auto-parking
- Wait a few seconds for GPS to lock before saving

---

## Tips & Best Practices

1. **Use Tailscale** for easy, secure access to your gateway from anywhere
2. **Keep the app updated** for the latest features and fixes
3. **Check reminders** — MyMate can set reminders via natural language
4. **Save memories** — Tell MyMate things to remember and it will!

---

## Need Help?

If you're still having issues:

1. Check the [README](../README.md) for general information
2. Review the [Privacy Policy](PRIVACY_POLICY.md)
3. Open an issue on GitHub with details about your problem

---

Happy chatting! 🎉
