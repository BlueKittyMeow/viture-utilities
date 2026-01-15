# Camera Integration Quick Start Guide
## Step-by-Step Implementation with Validation

**Companion to:** `CAMERA_INTEGRATION_PLAN.md` (comprehensive plan)
**Purpose:** Actionable checklist with validation commands after each step
**Estimated Time:** 8-13 hours total

---

## Prerequisites Checklist

Run these commands to verify you're ready to start:

```bash
# 1. Verify you're in the right directory
cd /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD
pwd
# Expected: /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD

# 2. Check Android SDK location
echo $ANDROID_HOME
# Expected: /home/bluekitty/Android/Sdk (or your SDK path)

# 3. Verify NDK is installed
ls ~/Android/Sdk/ndk/
# Expected: At least one version directory (e.g., 25.2.9519653)

# 4. Check current git branch
git branch
# Expected: * main

# 5. Verify phone is connected
adb devices
# Expected: 10.0.0.200:38315 or similar
```

**If any checks fail, stop and resolve before proceeding.**

---

## Phase 1: Clone & Setup (Est: 1 hour)

### Step 1.1: Clone UVCCamera Repository

```bash
cd /home/bluekitty/Documents/Git/viture-utilities/android-app
git clone https://github.com/saki4510t/UVCCamera.git
```

**Validate:**
```bash
ls -la UVCCamera/
# Expected: See directories: libuvccamera/, usbCameraCommon/, etc.

ls UVCCamera/libuvccamera/src/
# Expected: See main/ directory with Java/C++ source
```

**✅ Success:** Repository cloned with source code visible
**❌ If failed:** Check internet connection, try again

---

### Step 1.2: Configure NDK Path

```bash
# Get your NDK version
ls ~/Android/Sdk/ndk/
# Example output: 25.2.9519653

# Create local.properties in UVCCamera directory
cat > /home/bluekitty/Documents/Git/viture-utilities/android-app/UVCCamera/local.properties << 'EOF'
sdk.dir=/home/bluekitty/Android/Sdk
ndk.dir=/home/bluekitty/Android/Sdk/ndk/25.2.9519653
EOF
```

**⚠️ IMPORTANT:** Replace `25.2.9519653` with your actual NDK version from the `ls` command above!

**Validate:**
```bash
cat UVCCamera/local.properties
# Expected: See both sdk.dir and ndk.dir lines with your paths
```

**✅ Success:** local.properties exists with correct paths
**❌ If failed:** Check file was created, verify paths exist

---

### Step 1.3: Update VitureHUD settings.gradle.kts

**Backup current file:**
```bash
cp /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/settings.gradle.kts \
   /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/settings.gradle.kts.backup
```

**Edit file:** Add these lines to the END of `settings.gradle.kts`:
```kotlin
// Include UVCCamera modules
include(":libuvccamera")
include(":usbCameraCommon")

// Point to cloned UVCCamera modules
project(":libuvccamera").projectDir = file("../UVCCamera/libuvccamera")
project(":usbCameraCommon").projectDir = file("../UVCCamera/usbCameraCommon")
```

**Also add this to the repositories block in `dependencyResolutionManagement`:**
```kotlin
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://raw.github.com/saki4510t/libcommon/master/repository/") }
}
```

**Validate:**
```bash
grep "libuvccamera" /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/settings.gradle.kts
# Expected: See the include and projectDir lines

grep "saki4510t/libcommon" /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/settings.gradle.kts
# Expected: See the maven repository line
```

**✅ Success:** Both includes and repository added
**❌ If failed:** Check file syntax, ensure proper Kotlin formatting

---

### Step 1.4: Update app/build.gradle.kts Dependencies

**Backup current file:**
```bash
cp /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/build.gradle.kts \
   /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/build.gradle.kts.backup
```

**Add to dependencies block:**
```kotlin
dependencies {
    // ... existing dependencies ...

    // UVC Camera modules (local)
    implementation(project(":libuvccamera"))
    implementation(project(":usbCameraCommon"))

    // Required dependency for UVCCamera
    implementation("com.serenegiant:common:1.5.20") {
        exclude(module = "support-v4")
    }
}
```

**Validate:**
```bash
grep "libuvccamera" /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/build.gradle.kts
# Expected: See implementation(project(":libuvccamera"))

grep "serenegiant" /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/build.gradle.kts
# Expected: See implementation("com.serenegiant:common:1.5.20")
```

**✅ Success:** Dependencies added
**❌ If failed:** Check file syntax, ensure proper placement in dependencies block

---

### Step 1.5: Sync Gradle and Test Build

```bash
cd /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD
./gradlew clean
./gradlew build 2>&1 | tee build_log.txt
```

**Expected output (last lines):**
```
BUILD SUCCESSFUL in 45s
127 actionable tasks: 127 executed
```

**Validate:**
```bash
# Check if build succeeded
tail -5 build_log.txt | grep "BUILD SUCCESSFUL"

# Check if UVCCamera modules were compiled
find . -path "*/libuvccamera/build/intermediates" -type d
# Expected: Should find build artifacts

# Check APK was created
ls -lh app/build/outputs/apk/debug/app-debug.apk
# Expected: APK file exists (size ~5-10 MB)
```

**✅ Success:** Build successful, APK created
**❌ If failed:**
- Check build_log.txt for errors
- Common issues:
  - NDK path wrong → fix local.properties
  - Repository unreachable → check internet, maven URL
  - Gradle version mismatch → update gradle wrapper

**STOP HERE if build failed. Resolve before continuing.**

---

## Phase 2: VitureCamera Helper Class (Est: 2 hours)

### Step 2.1: Create VitureCamera.kt File

```bash
# Create file
touch /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/VitureCamera.kt
```

**Copy the complete VitureCamera.kt code from `CAMERA_INTEGRATION_PLAN.md` Section 4 (Phase 2).**

**Validate:**
```bash
# Check file exists and has content
wc -l /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/VitureCamera.kt
# Expected: ~200-300 lines

# Check for key class definition
grep "class VitureCamera" /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/VitureCamera.kt
# Expected: class VitureCamera(private val context: Context)

# Quick syntax check (will fail if there are obvious syntax errors)
cd /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD
./gradlew compileDebugKotlin 2>&1 | grep -i "error"
# Expected: No output (no errors)
```

**✅ Success:** File created, compiles without errors
**❌ If failed:** Check for syntax errors, missing imports

---

### Step 2.2: Quick Compilation Test

```bash
cd /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD
./gradlew :app:compileDebugKotlin
```

**Expected output:**
```
BUILD SUCCESSFUL
```

**Validate:**
```bash
# Check if VitureCamera class was compiled
find app/build -name "VitureCamera.class" -o -name "VitureCamera*.class"
# Expected: Should find compiled class files
```

**✅ Success:** VitureCamera.kt compiles successfully
**❌ If failed:** Fix Kotlin syntax errors before proceeding

---

## Phase 3: MainActivity Integration (Est: 2-3 hours)

### Step 3.1: Backup MainActivity.kt

```bash
cp /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/MainActivity.kt \
   /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/MainActivity.kt.backup
```

**Validate:**
```bash
diff /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/MainActivity.kt \
     /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/MainActivity.kt.backup
# Expected: No output (files identical)
```

---

### Step 3.2: Add Required Imports to MainActivity.kt

**Add at top of file:**
```kotlin
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

**Validate after adding:**
```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
# Expected: BUILD SUCCESSFUL (imports should compile)
```

---

### Step 3.3: Add Class Properties

**Add to MainActivity class (after existing properties):**
```kotlin
// Camera components
private var vitureCamera: VitureCamera? = null
private var surfaceView: SurfaceView? = null
private var isCameraMode = false
```

**Validate:**
```bash
grep "vitureCamera: VitureCamera" /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/MainActivity.kt
# Expected: See the property declaration
```

---

### Step 3.4: Update onCreate Method

Follow the code from `CAMERA_INTEGRATION_PLAN.md` Phase 3, Step 3.1

**Validate after changes:**
```bash
./gradlew :app:compileDebugKotlin
# Expected: BUILD SUCCESSFUL
```

---

### Step 3.5: Add Camera Mode Functions

Add all camera-related functions from the plan:
- `switchToTextMode()`
- `switchToCameraMode()`
- `createCameraSurface()`
- `handleCapture()`
- `capturePhoto()`
- `saveTextNote()`

**Validate:**
```bash
# Check all functions exist
for func in switchToTextMode switchToCameraMode createCameraSurface handleCapture capturePhoto saveTextNote; do
  echo "Checking $func..."
  grep "fun $func" /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/MainActivity.kt
done

# Compile
./gradlew :app:compileDebugKotlin
# Expected: BUILD SUCCESSFUL
```

---

### Step 3.6: Add onDestroy Cleanup

```kotlin
override fun onDestroy() {
    vitureCamera?.release()
    super.onDestroy()
}
```

**Validate:**
```bash
grep "fun onDestroy" /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/MainActivity.kt
# Expected: See the function
```

---

### Step 3.7: Full Build Test

```bash
cd /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD
./gradlew clean build 2>&1 | tee full_build_log.txt
```

**Expected:**
```
BUILD SUCCESSFUL
```

**Validate:**
```bash
# Check APK was created and is larger (has camera code now)
ls -lh app/build/outputs/apk/debug/app-debug.apk
# Expected: ~8-15 MB (larger than before)

# Check for no lint errors
grep -i "error" full_build_log.txt | grep -v "0 errors"
# Expected: No output (no errors)
```

**✅ Success:** Full app builds with camera integration
**❌ If failed:** Check full_build_log.txt, fix compilation errors

**STOP HERE if build failed. Do not install until build succeeds.**

---

## Phase 4: Installation & Basic Testing (Est: 1 hour)

### Step 4.1: Install to Phone

```bash
# Verify phone is connected
adb devices
# Expected: Your phone listed

# Install APK
cd /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Expected output:**
```
Performing Streamed Install
Success
```

**Validate:**
```bash
# Check app is installed
adb shell pm list packages | grep com.viture.hud
# Expected: package:com.viture.hud
```

**✅ Success:** App installed on phone
**❌ If failed:** Check adb connection, try uninstall first: `adb uninstall com.viture.hud`

---

### Step 4.2: Launch App (Without Glasses First)

```bash
# Launch app
adb shell am start -n com.viture.hud/.MainActivity

# Watch logs
adb logcat -s VitureCamera:D MainActivity:D
```

**Expected in logs:**
```
VitureCamera: USB monitor initialized
MainActivity: Activity created
```

**Validate:**
```bash
# Check app is running
adb shell ps | grep com.viture.hud
# Expected: See process running
```

**✅ Success:** App launches without crash
**❌ If crashed:** Check logcat for stack trace

---

### Step 4.3: Test Text Editor (No Camera Yet)

**Manual test on phone:**
1. Tap in text editor area
2. Type some text
3. Text should appear in green monospace font

**Validate:**
```bash
# Check logs for any errors
adb logcat -s VitureCamera:E MainActivity:E -d | tail -20
# Expected: No errors
```

**✅ Success:** Text editor works
**❌ If failed:** Check layout is visible, no UI crashes

---

### Step 4.4: Connect Glasses and Test USB Permission

**Steps:**
1. Connect Viture glasses to phone via USB-C
2. Watch logcat for USB events

```bash
adb logcat -s VitureCamera:D | grep -i "attach\|permission\|connect"
```

**Expected in logs:**
```
VitureCamera: Viture camera attached: VID=13770, PID=4353
VitureCamera: Permission denied for Viture camera (first time)
```

**Manual action required:**
- USB permission dialog should appear on phone
- Tap "Allow" and check "Always allow for this USB device"

**After granting permission, expected in logs:**
```
VitureCamera: Viture camera connected
VitureCamera: Camera opened successfully
VitureCamera: Camera configured: 1920x1080 @ 5fps
```

**Validate:**
```bash
# Check camera button is enabled
# (Manual: Check "Camera" button is no longer grayed out)
```

**✅ Success:** Camera detected and connected
**❌ If failed:**
- Check logs for specific errors
- Verify vendor/product ID matches (35ca:1101)
- Check device_filter.xml has correct IDs

---

### Step 4.5: Test Camera Mode Switch

**Steps:**
1. Tap "Camera" button
2. Watch for camera preview surface creation

```bash
adb logcat -s VitureCamera:D MainActivity:D | grep -i "preview\|surface"
```

**Expected:**
```
MainActivity: Switching to camera mode
VitureCamera: Starting preview...
VitureCamera: Preview started successfully
```

**Manual check:**
- Camera preview should display in content area
- Preview should NOT be blurry (should be sharp 1920x1080)

**Validate:**
```bash
# Check no errors
adb logcat -s VitureCamera:E MainActivity:E -d | tail -10
# Expected: No errors
```

**✅ Success:** Camera preview displays
**❌ If failed:**
- Check surface creation logs
- Try the retry logic if preview fails initially
- Check camera format detection logs

---

### Step 4.6: Test Still Image Capture

**Steps:**
1. In camera mode
2. Tap "Capture" button
3. Watch for save confirmation

```bash
adb logcat -s VitureCamera:D MainActivity:D | grep -i "capture\|saved"
```

**Expected:**
```
VitureCamera: Capturing still image...
VitureCamera: Image captured: 245632 bytes
MainActivity: Photo saved: viture_20260114_143052.jpg
```

**Validate captured image:**
```bash
# List captured photos
adb shell ls -lh /storage/emulated/0/Android/data/com.viture.hud/files/captures/

# Pull latest photo to check
adb pull /storage/emulated/0/Android/data/com.viture.hud/files/captures/ ./test_captures/

# Check image file
file test_captures/*.jpg
# Expected: JPEG image data, resolution 1920x1080

# View image (if you have imagemagick)
display test_captures/*.jpg
```

**✅ Success:** Photo captured at 1920x1080 resolution
**❌ If failed:**
- Check file permissions
- Check storage space
- Check image data is valid JPEG

---

### Step 4.7: Test Mode Switching Stability

**Steps (repeat 5 times):**
1. Switch to Camera mode
2. Wait 2 seconds
3. Switch to Text mode
4. Wait 2 seconds

```bash
# Monitor for memory leaks or errors
adb logcat -s VitureCamera:W VitureCamera:E MainActivity:E
```

**Expected:**
- No memory leak warnings
- No crashes
- Clean preview start/stop each time

**Validate:**
```bash
# Check app is still running after switches
adb shell ps | grep com.viture.hud
# Expected: Still running

# Check memory usage hasn't ballooned
adb shell dumpsys meminfo com.viture.hud | grep "TOTAL"
# Expected: Memory stable (< 100 MB)
```

**✅ Success:** Stable mode switching
**❌ If failed:** Check for resource leaks in camera release

---

## Phase 5: DeX Mode Testing (Est: 1-2 hours)

### Step 5.1: Enable DeX Mode

**Steps:**
1. Connect Viture glasses to phone
2. Enable DeX mode on phone
3. Check that app appears on glasses display

**Validate:**
```bash
# Check display configuration
adb shell dumpsys display | grep -A 5 "mDisplayId"
# Expected: See multiple displays listed
```

**Manual check:**
- App should appear on phone screen (not glasses) initially
- Drag app window to glasses display
- HUD layout should be visible on left side

**✅ Success:** App runs in DeX mode
**❌ If failed:** Check DeX is actually enabled

---

### Step 5.2: Test Camera in DeX Mode

**Repeat Step 4.5 and 4.6 but in DeX mode**

```bash
adb logcat -s VitureCamera:D | grep "preview\|capture"
```

**Expected:** Same behavior as non-DeX mode

**Validate:**
- Preview works in DeX
- Capture works in DeX
- No crashes when dragging app between displays

**✅ Success:** Camera works in DeX
**❌ If failed:** Check DeX-specific surface timing issues

---

## Troubleshooting Quick Reference

### Build Failed
```bash
# Check Gradle version
./gradlew --version

# Check NDK is accessible
ls ~/Android/Sdk/ndk/

# Re-sync Gradle
./gradlew --refresh-dependencies clean build
```

### Camera Not Detected
```bash
# Check USB device
adb shell lsusb | grep "35ca:1101"

# Check permissions in manifest
grep "usb.host" android-app/VitureHUD/app/src/main/AndroidManifest.xml

# Check device filter
cat android-app/VitureHUD/app/src/main/res/xml/device_filter.xml
```

### Preview Not Showing
```bash
# Check supported formats
adb logcat -s VitureCamera:D | grep "Supported"

# Try different format in VitureCamera.kt:
# Change FRAME_FORMAT_MJPEG to FRAME_FORMAT_YUYV

# Check bandwidth setting
# Try UVCCamera.BANDWIDTH_AUTO instead of DEFAULT_BANDWIDTH
```

---

## Success Criteria Checklist

- [ ] Phase 1: Build succeeds with UVCCamera modules
- [ ] Phase 2: VitureCamera.kt compiles without errors
- [ ] Phase 3: MainActivity builds with camera integration
- [ ] Phase 4.1: App installs on phone
- [ ] Phase 4.2: App launches without crash
- [ ] Phase 4.3: Text editor works
- [ ] Phase 4.4: Camera detected and connected
- [ ] Phase 4.5: Camera preview displays clearly
- [ ] Phase 4.6: Still image capture works (1920x1080)
- [ ] Phase 4.7: Mode switching is stable
- [ ] Phase 5.1: App runs in DeX mode
- [ ] Phase 5.2: Camera works in DeX mode

**When all checkboxes are ticked: Camera integration is complete! 🎉**

---

## Next Steps After Success

1. **Optional Enhancements** (See `CAMERA_INTEGRATION_PLAN.md` Phase 3.5):
   - Add coroutines for better lifecycle management
   - Implement USB permission persistence
   - Add automatic format detection
   - Add DeX surface retry logic

2. **Additional Features:**
   - Auto-save for text editor
   - File management UI
   - GPS location tagging on captures
   - Settings menu

3. **Performance Optimization:**
   - Reduce preview latency if needed
   - Optimize memory usage
   - Battery consumption profiling

---

**For complete code listings and detailed explanations, see:**
- `docs/CAMERA_INTEGRATION_PLAN.md` (comprehensive plan)
- `docs/claude-findings.md` (architecture decisions)
- `docs/gemini-findings.md` (code review suggestions)
- `docs/codex-findings.md` (lint and consistency checks)
