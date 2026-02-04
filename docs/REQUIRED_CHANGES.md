# Required Changes for Public Release

## Priority 1: Security (Before ANY Public Release)

### 1.1 Remove Your Auth Token from Source

**File:** `app/src/main/java/com/mymate/auto/data/local/PreferencesManager.kt`

```kotlin
// REMOVE THIS LINE:
const val DEFAULT_GATEWAY_TOKEN = "969802d413a94e7e4950fc6d12c441ea5b316b65df1fb7cb"

// REPLACE WITH:
const val DEFAULT_GATEWAY_TOKEN = ""
```

**File:** `app/src/main/java/com/mymate/auto/data/remote/MyMateApiClient.kt`

```kotlin
// REMOVE:
private const val DEFAULT_AUTH_TOKEN = "969802d413a94e7e4950fc6d12c441ea5b316b65df1fb7cb"

// REPLACE WITH:
private const val DEFAULT_AUTH_TOKEN = ""
```

### 1.2 Remove Your Tailscale IP as Default

**File:** `PreferencesManager.kt`
```kotlin
// Change:
const val DEFAULT_GATEWAY_URL = "ws://100.124.24.27:18789"
const val DEFAULT_WEBHOOK_URL = "http://100.124.24.27:18789/hooks/agent"

// To:
const val DEFAULT_GATEWAY_URL = ""
const val DEFAULT_WEBHOOK_URL = ""
```

---

## Priority 2: UX (For GitHub Release)

### 2.1 Add Setup Required Check

**File:** `MainActivity.kt` - Add check before showing main UI:

```kotlin
// Check if gateway is configured
val gatewayUrl = preferencesManager.getGatewayUrlSync()
val startDestination = if (gatewayUrl.isBlank()) "setup" else "chat"

NavHost(startDestination = startDestination) {
    composable("setup") {
        SetupRequiredScreen(onSetupComplete = { navController.navigate("chat") })
    }
    // ... rest of routes
}
```

### 2.2 Create SetupRequiredScreen

New file: `ui/setup/SetupRequiredScreen.kt`

```kotlin
@Composable
fun SetupRequiredScreen(onNavigateToSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Cloud, contentDescription = null, 
             modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Setup Required", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "MyMate requires an OpenClaw Gateway to function.\n\n" +
            "You need to run your own Gateway server and configure " +
            "the connection in Settings.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onNavigateToSettings) {
            Text("Open Settings")
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { /* open browser to docs */ }) {
            Text("Learn More →")
        }
    }
}
```

---

## Priority 3: Documentation (Essential)

### 3.1 Create README.md

```markdown
# MyMate - AI Assistant for Android Auto 🦞

Personal AI assistant app with Android Auto support, powered by OpenClaw Gateway.

## ⚠️ Requirements

This app **requires** an [OpenClaw Gateway](https://github.com/openclaw/openclaw) 
running on your own server. MyMate is the mobile client - it cannot work standalone.

## Features

- 💬 Chat with your AI assistant
- 🎤 Voice input while driving
- 🚗 Full Android Auto integration
- 📍 Save parking locations
- 🧠 Store memories and notes
- ⏰ Reminders with notifications

## Setup

1. Install OpenClaw Gateway on your server/Pi
2. Configure Tailscale or expose the Gateway port
3. Install MyMate on your phone
4. Go to Settings → Gateway URL → Enter your Gateway address
5. Enter your auth token if required

## Screenshots

[Add screenshots here]

## Building from Source

```bash
git clone https://github.com/mypersonalcodebox/mymate-auto
cd mymate-auto
./gradlew assembleDebug
```

## License

[Add license]
```

---

## Priority 4: Code Cleanup

### 4.1 Unify Port References

Several files use hardcoded port 18791 (legacy?):
- `auto/DeveloperActionsScreen.kt`
- `auto/MainAutoScreen.kt`
- `auto/MyMateCarAppService.kt`
- `auto/SettingsAutoScreen.kt`
- `data/remote/WebSocketManager.kt`

All should use PreferencesManager settings instead of hardcoded values.

### 4.2 Fix Network Security Config

**File:** `res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Allow cleartext for local development and self-hosted servers -->
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    <!-- Note: In production, users should use HTTPS/WSS -->
</network-security-config>
```

Remove hardcoded IP. The `base-config` with `cleartextTrafficPermitted="true"` 
allows cleartext to any host (needed for self-hosted setups without SSL).

---

## Files to Create

- [ ] `README.md` (root)
- [ ] `SETUP.md` (detailed setup guide)
- [ ] `ui/setup/SetupRequiredScreen.kt`

## Files to Modify

- [ ] `data/local/PreferencesManager.kt` (remove credentials)
- [ ] `data/remote/MyMateApiClient.kt` (remove token)
- [ ] `data/remote/OpenClawWebSocket.kt` (remove hardcoded URL)
- [ ] `data/remote/WebSocketManager.kt` (remove hardcoded URL)
- [ ] `res/xml/network_security_config.xml` (generalize)
- [ ] `MainActivity.kt` (add setup check)
- [ ] Various `auto/*.kt` files (use settings not hardcoded URLs)

---

## Quick Security Fix (Do This NOW)

If you want to publish source code today, at minimum:

```bash
cd /tmp/mymate-auto

# Find and replace your token
grep -r "969802d413a94e7e4950fc6d12c441ea5b316b65df1fb7cb" --include="*.kt" -l | \
  xargs sed -i 's/969802d413a94e7e4950fc6d12c441ea5b316b65df1fb7cb//g'

# Find and replace your IP
grep -r "100.124.24.27" --include="*.kt" --include="*.xml" -l | \
  xargs sed -i 's/100.124.24.27//g'
```

Then manually fix the empty strings to have sensible defaults or empty-string handling.
