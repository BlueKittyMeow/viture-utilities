# Viture HUD Android App - Development Plan

## ✅ Confirmed: Camera Access Works!

**Test Result:** CameraFi successfully detected Viture camera as "USB 2.0 Camera"
- Camera IS accessible via Android USB Host API
- No root required
- Works in DeX mode
- Blurry/low-res due to wrong settings (fixable)

## App Architecture

### Core Components

#### 1. USB Camera Interface
**Library:** [UVCCamera by saki4510t](https://github.com/saki4510t/UVCCamera)
- Proven library for USB cameras on Android
- Supports UVC protocol
- Can request specific resolutions/framerates

**Configuration:**
```java
// Request native Viture resolution
camera.setPreviewSize(1920, 1080, UVCCamera.FRAME_FORMAT_YUYV);
camera.setPreviewFps(5); // Viture runs at 5fps
```

#### 2. DeX-Optimized HUD Overlay
**Layout:**
```
┌─────────────────────────────────────┐
│ [Camera] [Settings]          [Menu] │ ← Top bar
│                                     │
│                                     │
│         Transparent/Black           │
│         (See-through area)          │
│                                     │
│  ┌─────────────────────────┐       │
│  │ Text Editor Area        │       │ ← Bottom corner
│  │ Type notes here...      │       │
│  │                         │       │
│  └─────────────────────────┘       │
└─────────────────────────────────────┘
```

**Features:**
- Partial screen overlay (adjustable size)
- Black background = transparent in glasses
- Green terminal-style text (high contrast)
- Resizable/draggable HUD windows

#### 3. Text Editor Module
**Features:**
- Multi-line text input
- Auto-save to local storage
- Export to text files
- Keyboard shortcuts (for DeX keyboard)
- Word count for novel writing

**Storage:**
```
/storage/emulated/0/VitureHUD/
  ├── notes/
  │   ├── 2026-01-14_note1.txt
  │   └── 2026-01-14_note2.txt
  └── captures/
      ├── 2026-01-14_143052.jpg
      └── 2026-01-14_143112.jpg
```

#### 4. Camera Capture Module
**Features:**
- Capture button in HUD
- Keyboard shortcut (e.g., Ctrl+Shift+C)
- Timestamped filenames
- Background capture (don't block UI)
- Save to Gallery + app folder

**Implementation:**
```java
// Capture at full resolution
camera.captureStill("/storage/.../capture.jpg", 1920, 1080);
```

#### 5. Navigation Overlay (Future)
**Options:**
- Google Maps API integration
- Screen scraping from Maps app
- OpenStreetMap + routing API
- Display next turn + ETA in corner

### Permissions Required

```xml
<uses-permission android:name="android.permission.USB_HOST" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.INTERNET" /> <!-- for navigation -->
<uses-feature android:name="android.hardware.usb.host" />
```

## Development Phases

### Phase 1: Proof of Concept (MVP)
**Goal:** Basic HUD with camera view

**Tasks:**
1. Set up Android Studio project
2. Integrate UVCCamera library
3. Detect Viture camera (vendor 0x35ca)
4. Display camera preview at 1920x1080
5. Simple black background with single text field
6. Camera capture button

**Estimated Time:** 1-2 days

**Success Criteria:**
- ✓ Camera displays clearly (not blurry)
- ✓ Can capture photos
- ✓ Basic text input works

### Phase 2: Enhanced HUD
**Goal:** Usable text editor + better UI

**Tasks:**
1. Multi-line text editor with scroll
2. Auto-save functionality
3. File management (list/open/delete notes)
4. Adjustable HUD size/position
5. Settings menu
6. Keyboard shortcuts

**Estimated Time:** 2-3 days

**Success Criteria:**
- ✓ Can write and save notes comfortably
- ✓ HUD doesn't obstruct view too much
- ✓ Works well in DeX mode

### Phase 3: Polish + Navigation
**Goal:** Production-ready app

**Tasks:**
1. Navigation overlay integration
2. Custom camera controls (exposure, focus if supported)
3. UI themes (different colors/styles)
4. Widget support for quick capture
5. Backup/export to cloud
6. Battery optimization

**Estimated Time:** 3-5 days

**Success Criteria:**
- ✓ Navigation works reliably
- ✓ App is stable for daily use
- ✓ Battery efficient

## Technical Considerations

### Camera Resolution Issue (Current)
**Problem:** CameraFi shows blurry image
**Cause:** App likely using 640x480 or 800x600 default
**Solution:** Explicitly request 1920x1080 YUYV format

### DeX Mode Compatibility
**Requirements:**
- Use `android:resizeableActivity="true"` for multi-window
- Handle screen rotation
- Support mouse + keyboard input
- Optimize for landscape orientation

### Power Consumption
**Strategies:**
- Don't continuously preview camera (only when needed)
- Use efficient rendering (hardware acceleration)
- Minimize wake locks
- Background services only when necessary

**Expected Battery Life:**
- Text editor only: ~10-12 hours
- With camera preview: ~6-8 hours
- With navigation: ~5-7 hours

## Development Tools

### Required Software
- **Android Studio** (latest version)
- **Kotlin** or Java (recommend Kotlin)
- **Gradle** for build management
- **UVCCamera library** (add as dependency)

### Testing Hardware
- Samsung Galaxy S22 Ultra (your phone)
- Viture Luma Pro glasses
- USB-C connection
- DeX mode (wired or wireless)

## Code Structure

```
VitureHUD/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/viture/hud/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── camera/
│   │   │   │   │   ├── UVCCameraManager.kt
│   │   │   │   │   └── CaptureService.kt
│   │   │   │   ├── editor/
│   │   │   │   │   ├── TextEditorFragment.kt
│   │   │   │   │   └── FileManager.kt
│   │   │   │   ├── navigation/
│   │   │   │   │   └── NavigationOverlay.kt
│   │   │   │   └── ui/
│   │   │   │       ├── HUDOverlay.kt
│   │   │   │       └── SettingsActivity.kt
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   ├── values/
│   │   │   │   └── xml/
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   └── libs/
│       └── UVCCamera.aar
└── README.md
```

## Next Steps

1. **Set up development environment**
   - Install Android Studio
   - Create new Kotlin project
   - Add UVCCamera library dependency

2. **Build Phase 1 MVP**
   - Start with camera detection
   - Get 1920x1080 preview working
   - Add basic HUD overlay
   - Test on phone with glasses

3. **Iterate based on testing**
   - Fix camera quality issues
   - Optimize UI for glasses display
   - Add features incrementally

## Questions to Answer

- [ ] Do you have Android development experience?
- [ ] Kotlin or Java preference?
- [ ] Should I help set up the project?
- [ ] Want to start with camera module or HUD UI first?
- [ ] Any specific features to prioritize?

## Resources

- **UVCCamera Library:** https://github.com/saki4510t/UVCCamera
- **Android USB Host API:** https://developer.android.com/guide/topics/connectivity/usb/host
- **DeX Guidelines:** https://developer.samsung.com/samsung-dex/guidelines.html
- **Viture Specs:** 1920x1080 @ 5fps, vendor 0x35ca, product 0x1101

---

**Bottom Line:** This is 100% achievable as a phone-only solution. Camera quality will be much better once we request the right resolution. No external hardware needed!
