# Quick Setup Guide - Viture HUD Android App

## Option 1: Use Android Studio (Recommended)

### Step 1: Install Android Studio
```bash
# Download from: https://developer.android.com/studio
# Install and launch
```

### Step 2: Open the Project
1. Launch Android Studio
2. **File → Open**
3. Navigate to: `/home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD`
4. Click "OK"

### Step 3: Sync Gradle
- Android Studio will automatically prompt: **"Gradle files have changed since last sync"**
- Click **"Sync Now"**
- Wait for: **"BUILD SUCCESSFUL"**

### Step 4: Connect Your Phone
```bash
# Already done, but to verify:
adb devices
# Should show: 10.0.0.200:38315
```

### Step 5: Run the App
1. Make sure phone is selected in device dropdown (top toolbar)
2. Click green **"Run"** button (▶️)
3. App will build and install to phone

### Step 6: Test
1. Connect Viture glasses to phone via USB-C
2. Start DeX mode
3. Launch "Viture HUD" app on phone
4. App should appear fullscreen on glasses
5. Camera should auto-connect

---

## Option 2: Command Line Build

### Step 1: Navigate to Project
```bash
cd /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD
```

### Step 2: Build APK
```bash
# First time: make gradlew executable
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug
```

### Step 3: Install to Phone
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 4: Launch
```bash
# From phone: Open "Viture HUD" app
# Or from laptop:
adb shell am start -n com.viture.hud/.MainActivity
```

---

## Common First-Time Issues

### Issue: "Gradle sync failed"
**Solution:**
1. Check internet connection (needs to download dependencies)
2. Wait and try again
3. Or: Tools → Android SDK Manager → Install latest SDK

### Issue: "Device not found"
**Solution:**
```bash
# Reconnect via WiFi debugging
adb connect 10.0.0.200:38315
```

### Issue: "App crashes on launch"
**Solution:**
1. Check phone logs:
   ```bash
   adb logcat | grep VitureHUD
   ```
2. Look for error messages
3. Most common: USB permission issues (grant permission when prompted)

---

## Expected First Run

1. **App launches** on phone screen (if DeX not active)
2. **USB permission popup** - Tap "Allow" and check "Always allow"
3. **Camera connects** - Toast message: "Camera connected"
4. **Green text editor** appears at bottom
5. **"Capture" button** at top

If glasses are connected in DeX:
- App should launch **on glasses display** instead
- Everything else same

---

## Quick Test Checklist

After building and installing:

- [ ] App launches without crashing
- [ ] Text editor visible (green text on black)
- [ ] Can type in text editor
- [ ] "Capture" button visible
- [ ] Connect glasses → "Camera connected" toast
- [ ] Camera preview appears (optional)
- [ ] Capture button saves photo

All checked? **You're good to go!** 🎉

---

## File Locations on Phone

After installation:

**App Location:**
```
/data/app/com.viture.hud/
```

**Captured Photos:**
```
/Android/data/com.viture.hud/files/captures/
```

**Access photos:**
```bash
adb shell ls /storage/emulated/0/Android/data/com.viture.hud/files/captures/
adb pull /storage/emulated/0/Android/data/com.viture.hud/files/captures/viture_*.jpg .
```

---

## Next: Customize the App

See `VitureHUD/README.md` for:
- Adjusting HUD size
- Changing colors
- Adding features
- Troubleshooting

---

**Ready to build?** Run Android Studio or `./gradlew assembleDebug`!
