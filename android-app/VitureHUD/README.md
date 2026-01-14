# Viture HUD - Android App

Native Android app for Viture Luma Pro glasses with HUD overlay, camera capture, and text editor.

## ✅ What This App Does

1. **Launches fullscreen on Viture glasses** (secondary HDMI display in DeX mode)
2. **Camera access at native 1920x1080** resolution (fixes CameraFi blurriness)
3. **Camera capture** with timestamped photos
4. **Text editor** for notes/novel writing with green terminal-style text
5. **Black background** = transparent in glasses for see-through view

## 🏗️ Setup Instructions

### Prerequisites

1. **Android Studio** (latest version)
   - Download from: https://developer.android.com/studio

2. **Java 17** (usually bundled with Android Studio)

3. **Samsung phone with DeX** (you have S22 Ultra ✓)

4. **Viture Luma Pro glasses** ✓

### Installation Steps

1. **Open project in Android Studio:**
   ```bash
   cd /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD
   # Then: File → Open in Android Studio
   ```

2. **Sync Gradle:**
   - Android Studio will prompt to sync
   - This downloads the UVCCamera library and dependencies
   - Wait for "BUILD SUCCESSFUL"

3. **Connect phone via USB debugging:**
   ```bash
   adb devices
   # Your phone should appear: 10.0.0.200:38315
   ```

4. **Build and install:**
   - Click green "Run" button in Android Studio
   - Or: `./gradlew installDebug` from terminal

5. **Connect Viture glasses to phone**

6. **Launch app in DeX mode**

## 📐 How It Works

### Display Targeting (DeX Mode)

The app automatically detects and targets the secondary display (Viture glasses):

```kotlin
// In MainActivity.kt
private fun targetGlassesDisplay() {
    val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val displays = displayManager.displays

    for (display in displays) {
        if (display.displayId > 0) {  // Secondary display = glasses
            moveToDisplay(display)
            return
        }
    }
}
```

**Why this matters:** Without targeting the correct display, the app opens on your phone screen instead of the glasses.

### Camera Setup (Native Resolution)

The app requests **1920x1080 @ YUYV** format - the native Viture resolution:

```kotlin
camera?.setPreviewSize(
    1920,
    1080,
    UVCCamera.FRAME_FORMAT_YUYV  // This fixes the blurry CameraFi issue
)
```

**CameraFi was blurry because:** It defaulted to 640x480 or 800x600.

### USB Device Detection

The app automatically detects Viture cameras via USB vendor/product ID:

```xml
<!-- device_filter.xml -->
<usb-device vendor-id="13770" product-id="4353" />
<!-- 0x35ca = 13770, 0x1101 = 4353 -->
```

## 🎮 Usage

1. **Launch app** - It opens fullscreen on glasses
2. **Camera auto-connects** when glasses are plugged in
3. **Type in text editor** - Green text on black background
4. **Press "Capture" button** - Takes photo at full 1920x1080
5. **Photos saved to:** `/Android/data/com.viture.hud/files/captures/`

### Text Editor Features

- Multi-line input
- Monospace font (good for writing)
- Auto-save on pause (TODO)
- Green terminal-style aesthetic

### Camera Features

- Optional preview (can hide to maximize see-through)
- Full resolution capture (1920x1080)
- Timestamped filenames

## 🔧 Configuration Options

### Adjust HUD Size

Edit `activity_main.xml` line 62:

```xml
app:layout_constraintWidth_percent="0.5"  <!-- 50% of screen -->
<!-- Change to 0.3 for 30%, 0.7 for 70%, etc. -->
```

### Hide Camera Preview

Set visibility to `gone` by default (line 49):

```xml
android:visibility="gone"  <!-- Already set -->
```

Add a toggle button if you want to show/hide it dynamically.

### Change Text Color

Edit `activity_main.xml` line 69:

```xml
android:textColor="#00FF00"  <!-- Green -->
<!-- Try: #0099FF (blue), #FF9900 (orange), etc. -->
```

## 📱 Testing Checklist

- [ ] App launches on glasses display (not phone screen)
- [ ] Camera connects and shows clear image
- [ ] Capture button saves photos
- [ ] Text editor accepts input
- [ ] Black background looks transparent in glasses
- [ ] No crashes when glasses disconnected

## 🐛 Troubleshooting

### App opens on phone screen, not glasses

**Problem:** DeX mode not active or display detection failed

**Fix:**
1. Ensure DeX is running (glasses connected)
2. Check logs: `adb logcat | grep VitureHUD`
3. Look for: "Found X displays" and "Targeting display Y"

### Camera not connecting

**Problem:** USB permission not granted

**Fix:**
1. Watch for USB permission popup on phone
2. Tap "Allow" and check "Always allow"
3. If no popup: disconnect/reconnect glasses

### Blurry camera image

**Problem:** Wrong resolution selected

**Fix:**
- Check MainActivity.kt line 155 says `1920, 1080`
- Not `640, 480` or other resolutions

### App crashes on launch

**Problem:** Likely missing dependency or SDK version

**Fix:**
1. Check Android Studio "Build" tab for errors
2. Sync Gradle again
3. Ensure minSdk 26 or higher

## 🚀 Next Steps / TODOs

### Phase 1 Enhancements
- [ ] Auto-save text editor content
- [ ] File management (open/save/list notes)
- [ ] Keyboard shortcuts (Ctrl+S to save, Ctrl+Shift+C to capture)
- [ ] Settings screen (adjust colors, sizes, etc.)

### Phase 2 Features
- [ ] Navigation overlay (Google Maps integration)
- [ ] Word count display for novel writing
- [ ] Multiple HUD layouts (minimal, full, custom)
- [ ] Export notes to cloud storage

### Phase 3 Polish
- [ ] Custom camera controls (exposure, brightness)
- [ ] Voice input for hands-free notes
- [ ] Widget for quick capture
- [ ] Battery optimization

## 📂 Project Structure

```
VitureHUD/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/viture/hud/
│   │   │   └── MainActivity.kt          ← Main app logic
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml    ← UI layout
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   └── themes.xml           ← Black background theme
│   │   │   └── xml/
│   │   │       └── device_filter.xml    ← Viture USB IDs
│   │   └── AndroidManifest.xml          ← Permissions & config
│   └── build.gradle.kts                 ← Dependencies
├── build.gradle.kts
├── settings.gradle.kts
└── README.md                            ← This file
```

## 🔑 Key Code Sections

### MainActivity.kt

- **Lines 50-65:** Display targeting for DeX mode
- **Lines 89-115:** USB device detection
- **Lines 120-150:** Camera initialization
- **Lines 155-157:** **CRITICAL:** Native resolution setting
- **Lines 182-205:** Photo capture

### activity_main.xml

- **Lines 9-10:** Black background
- **Lines 37-44:** Camera preview widget
- **Lines 47-70:** Text editor widget

## 💡 Tips for Development

1. **Use Android Studio's Logcat** to see debug messages:
   ```
   Tag: VitureHUD
   ```

2. **Test without glasses first:**
   - App will work on phone screen
   - Camera won't connect (no USB)
   - But UI and text editor will work

3. **Iterate quickly:**
   - Change UI in XML (instant preview)
   - Test on phone screen first
   - Then test with glasses

4. **Common Kotlin patterns:**
   ```kotlin
   // Null safety
   camera?.startPreview()  // Only calls if camera != null

   // Lambda functions
   button.setOnClickListener { capturePhoto() }

   // String templates
   Log.d(TAG, "Display ${display.displayId}")
   ```

## 📚 Resources

- **UVCCamera Library:** https://github.com/saki4510t/UVCCamera
- **Android USB Host:** https://developer.android.com/guide/topics/connectivity/usb/host
- **Samsung DeX Guidelines:** https://developer.samsung.com/samsung-dex
- **Kotlin Docs:** https://kotlinlang.org/docs/home.html

## 🎯 Success Criteria

✅ **MVP Complete when:**
- App launches on glasses display
- Camera shows clear 1920x1080 image
- Can capture and save photos
- Can type and edit notes
- Runs without crashes

---

**Current Status:** MVP code complete, ready for testing!

Next: Build and test on your phone with glasses connected.
