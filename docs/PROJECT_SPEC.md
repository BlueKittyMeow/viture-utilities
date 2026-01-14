# Viture HUD - Project Specification

## Project Overview

**Name:** Viture HUD
**Platform:** Android (native app)
**Target Device:** Samsung phones with DeX mode + Viture Luma Pro glasses
**Primary Use Cases:**
- Novel/note writing with AR overlay
- Photo capture for documentation/memory
- Navigation assistance (future)
- Hands-free information access

**Core Philosophy:** Minimal obstruction, maximum utility. Black = transparent in glasses for see-through operation.

---

## Current Features (MVP - v1.0)

### ✅ Display Management
- **DeX Display Targeting**
  - Auto-detects secondary display (Viture glasses on HDMI)
  - Launches fullscreen on glasses display
  - Falls back to primary display if DeX not active
  - Handles display changes gracefully

### ✅ Camera Integration
- **USB Camera Access**
  - Uses UVCCamera library (USB Host API)
  - Auto-detects Viture camera (vendor 0x35ca, product 0x1101)
  - Requests native 1920x1080 @ 5fps resolution
  - Fixes blurry image issue from generic apps

- **Photo Capture**
  - One-button capture
  - Saves at full 1920x1080 resolution
  - Timestamped filenames: `viture_YYYY-MM-DD_HHmmss.jpg`
  - Stored in: `/Android/data/com.viture.hud/files/captures/`
  - Toast notification on successful capture

- **Camera Preview**
  - Optional preview window (can be hidden)
  - 400x225 preview size (16:9 aspect ratio)
  - Top-left positioning by default
  - Minimal performance impact

### ✅ Text Editor
- **Basic Editor**
  - Multi-line text input
  - Monospace font (good for writing)
  - Green terminal-style text (#00FF00)
  - Dark background (#0A0A0A) with transparency
  - 50% screen width by default (adjustable)
  - Bottom positioning for minimal obstruction

- **Input Support**
  - Physical keyboard (DeX mode)
  - Touch input (if needed)
  - No auto-correct (textNoSuggestions flag)

### ✅ UI/UX
- **Black Background Theme**
  - Full black (#000000) = transparent in Viture glasses
  - Allows see-through operation
  - Minimal power consumption

- **Simple Controls**
  - Capture button (top-left, green)
  - Settings button (top-right)
  - No clutter, focused interface

### ✅ USB Device Management
- **Auto-Detection**
  - Monitors USB devices
  - Auto-requests permission for Viture camera
  - Handles connect/disconnect gracefully
  - Shows toast notifications for status

---

## Planned Features & Roadmap

### Phase 2: Enhanced Editor (v1.1)
**Priority:** High
**Timeline:** 1-2 weeks

#### File Management
- [ ] **Save/Load Notes**
  - Auto-save on pause/exit
  - Manual save with Ctrl+S
  - File browser to open existing notes
  - Folder organization by date/project

- [ ] **Note Metadata**
  - Creation/modification timestamps
  - Word count display (live counter)
  - Character count
  - Reading time estimate

- [ ] **Export Options**
  - Export to plain text (.txt)
  - Export to Markdown (.md)
  - Share via Android share sheet
  - Cloud backup (Google Drive, Dropbox)

#### Editor Enhancements
- [ ] **Formatting Support**
  - Basic Markdown rendering (optional)
  - Syntax highlighting for code (if enabled)
  - Font size adjustment
  - Line numbers (optional)

- [ ] **Keyboard Shortcuts**
  - Ctrl+S: Save
  - Ctrl+Shift+C: Capture photo
  - Ctrl+N: New note
  - Ctrl+O: Open note
  - Ctrl+F: Find in text

- [ ] **Writing Tools**
  - Word count goal tracker
  - Pomodoro timer integration
  - Distraction-free mode (hide all UI except text)

### Phase 3: Advanced Camera Features (v1.2)
**Priority:** High
**Timeline:** 1-2 weeks

#### Photo Metadata & Tagging
- [ ] **Location Tagging** 🆕
  - Capture GPS coordinates at photo time
  - Requires `ACCESS_FINE_LOCATION` permission
  - Embed EXIF GPS data in JPEG
  - Fallback to network location if GPS unavailable
  - Show location in photo gallery view

  **Technical Details:**
  ```kotlin
  // Permissions needed:
  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
  <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

  // Data captured:
  - Latitude/Longitude
  - Altitude (if available)
  - Accuracy estimate
  - Timestamp
  - Address (reverse geocoding)
  ```

- [ ] **Photo Organization**
  - Gallery view of captured photos
  - Thumbnails with metadata preview
  - Filter by date/location
  - Delete/rename photos
  - Batch export

- [ ] **Enhanced Metadata**
  - Device info (phone model, Android version)
  - Camera settings (if available)
  - Notes/captions per photo
  - Tags/categories
  - Associated text note (link photo to writing session)

#### Camera Controls
- [ ] **Manual Controls** (if supported by Viture camera)
  - Exposure adjustment
  - Brightness/contrast
  - Focus mode (if available)
  - White balance

- [ ] **Capture Modes**
  - Burst mode (multiple photos rapidly)
  - Timer/delay capture
  - Interval capture (timelapse)
  - Motion detection capture

- [ ] **Video Recording** (stretch goal)
  - Record short clips
  - Timestamped video files
  - Basic playback

### Phase 4: Navigation Overlay (v1.3)
**Priority:** Medium
**Timeline:** 2-3 weeks

#### Google Maps Integration
- [ ] **Turn-by-Turn Navigation**
  - Display next turn instruction
  - Distance to next turn
  - ETA to destination
  - Current speed (if moving)

- [ ] **Minimal Overlay Design**
  - Corner overlay (adjustable position)
  - Arrow icons for turns
  - Text instructions
  - Auto-hide when not navigating

- [ ] **Maps API Integration**
  - Google Maps Directions API
  - Real-time updates
  - Rerouting on deviation
  - Alternative routes

#### Navigation Features
- [ ] **Walking Navigation**
  - Optimized for pedestrian use
  - Nearby landmarks
  - Points of interest
  - AR waypoint markers (stretch goal)

- [ ] **Cycling Navigation**
  - Bike-friendly routes
  - Elevation profile
  - Safety warnings

- [ ] **Voice Guidance**
  - Text-to-speech turn instructions
  - Headphone/speaker support
  - Adjustable volume

### Phase 5: Advanced Features (v2.0)
**Priority:** Low-Medium
**Timeline:** 1-2 months

#### Multi-Layout System
- [ ] **Layout Presets**
  - Minimal: Text only, small corner box
  - Writing: Large text area, no camera
  - Photo: Large camera preview, small text
  - Navigation: Map overlay, minimal text
  - Custom: User-defined layout

- [ ] **Layout Switching**
  - Quick toggle between layouts
  - Gesture controls
  - Voice commands (stretch)
  - Context-aware switching

#### Voice Integration
- [ ] **Voice Input**
  - Dictation to text editor
  - Hands-free note taking
  - Voice commands ("capture photo", "save note")
  - Background listening (optional)

- [ ] **Text-to-Speech**
  - Read notes aloud
  - Navigation instructions
  - Notifications

#### Widget System
- [ ] **Quick Capture Widget**
  - Home screen widget
  - Notification quick action
  - Lock screen shortcut

- [ ] **Status Widget**
  - Show word count goal
  - Next navigation instruction
  - Recent notes

#### Customization
- [ ] **Themes**
  - Color schemes (green, blue, amber, red)
  - Font selection
  - Size/opacity adjustments
  - Day/night modes

- [ ] **UI Positioning**
  - Drag-and-drop HUD elements
  - Resize widgets
  - Save custom layouts
  - Multi-display profiles

### Phase 6: Collaboration & Sync (v2.1)
**Priority:** Low
**Timeline:** 1-2 months

#### Cloud Integration
- [ ] **Real-time Sync**
  - Sync notes across devices
  - Version history
  - Conflict resolution
  - Offline mode with sync queue

- [ ] **Cloud Storage**
  - Google Drive integration
  - Dropbox support
  - Custom WebDAV server
  - End-to-end encryption option

#### Sharing & Collaboration
- [ ] **Share Notes**
  - Generate shareable links
  - Export as PDF
  - Email integration
  - Markdown to HTML conversion

- [ ] **Photo Sharing**
  - Share with location data
  - Privacy controls (strip EXIF)
  - Social media integration
  - Custom galleries

### Phase 7: Advanced AR Features (v3.0)
**Priority:** Low (Experimental)
**Timeline:** 3-6 months

#### AR Overlay Enhancements
- [ ] **Object Recognition**
  - Identify objects in camera view
  - Show information overlay
  - Translation of text (OCR + translate)

- [ ] **Spatial Anchors**
  - Place virtual notes in 3D space
  - Leave location-based notes
  - AR waypoint markers

- [ ] **QR Code Scanner**
  - Auto-detect and decode QR codes
  - Open URLs, add contacts, etc.
  - Barcode scanning

#### Context-Aware Features
- [ ] **Smart Suggestions**
  - Suggest notes based on location
  - Remind about unfinished writing
  - Context-aware photo capture

- [ ] **Activity Detection**
  - Detect walking, sitting, etc.
  - Adjust UI accordingly
  - Power optimization

---

## Technical Specifications

### Dependencies

```gradle
// Core Android
androidx.core:core-ktx:1.12.0
androidx.appcompat:appcompat:1.6.1
com.google.android.material:material:1.11.0
androidx.constraintlayout:constraintlayout:2.1.4

// USB Camera
com.github.saki4510t:UVCCamera:v3.1.0

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3

// Future: Location
com.google.android.gms:play-services-location:21.0.1

// Future: Maps
com.google.android.gms:play-services-maps:18.2.0

// Future: EXIF handling
androidx.exifinterface:exifinterface:1.3.6

// Future: Room database
androidx.room:room-runtime:2.6.0
androidx.room:room-ktx:2.6.0
```

### Permissions

#### Current (v1.0)
```xml
<uses-permission android:name="android.permission.USB_HOST" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

#### Future Additions
```xml
<!-- Phase 3: Location tagging -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Phase 4: Navigation -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Phase 5: Voice input -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Phase 6: Cloud sync -->
<uses-permission android:name="android.permission.GET_ACCOUNTS" />

<!-- Phase 7: AR features -->
<uses-permission android:name="android.permission.CAMERA" />
```

### Storage Structure

```
/Android/data/com.viture.hud/files/
├── captures/                    # Photo captures
│   ├── 2026-01-14_143052.jpg   # With EXIF GPS data (Phase 3)
│   └── 2026-01-14_150230.jpg
├── notes/                       # Text notes (Phase 2)
│   ├── 2026-01-14_journal.txt
│   ├── novel_chapter1.txt
│   └── ideas.md
├── layouts/                     # Custom layouts (Phase 5)
│   ├── minimal.json
│   └── writing.json
└── cache/                       # Temporary files
    └── thumbnails/
```

### Data Models (Future)

#### Photo Metadata
```kotlin
data class PhotoCapture(
    val id: String,
    val timestamp: Long,
    val filepath: String,
    val location: Location?,      // GPS coordinates
    val address: String?,         // Reverse geocoded address
    val altitude: Double?,
    val accuracy: Float?,
    val deviceInfo: DeviceInfo,
    val associatedNoteId: String?, // Link to note
    val tags: List<String>,
    val caption: String?
)

data class Location(
    val latitude: Double,
    val longitude: Double
)
```

#### Note Metadata
```kotlin
data class Note(
    val id: String,
    val filename: String,
    val content: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val wordCount: Int,
    val characterCount: Int,
    val tags: List<String>,
    val associatedPhotos: List<String>, // Photo IDs
    val location: Location?  // Where note was written
)
```

---

## Performance Targets

### Battery Life
- **Text editor only:** 10-12 hours
- **With camera preview:** 6-8 hours
- **With navigation:** 5-7 hours
- **All features active:** 4-6 hours

### Camera Performance
- **Capture latency:** < 500ms
- **Preview framerate:** 5fps (Viture native)
- **Photo save time:** < 1 second

### UI Performance
- **App launch time:** < 2 seconds
- **Display targeting:** < 500ms
- **Text input lag:** < 50ms
- **Smooth scrolling:** 60fps minimum

---

## Testing Strategy

### Unit Tests (Future)
- Location tagging logic
- EXIF data writing
- File management operations
- Note save/load functions

### Integration Tests (Future)
- USB camera connection flow
- Display targeting in DeX mode
- Photo capture with GPS tagging
- Cloud sync operations

### Manual Testing Checklist
- [ ] App launches on glasses display
- [ ] Camera connects and shows clear image
- [ ] Photo capture works and saves correctly
- [ ] Text editor accepts input smoothly
- [ ] Display switching works (phone ↔ glasses)
- [ ] USB disconnect/reconnect handled gracefully
- [ ] Battery usage acceptable
- [ ] No crashes during extended use

---

## Open Questions & Considerations

### Privacy & Security
- **Location data:** User consent required, option to disable
- **Photo EXIF:** Option to strip location before sharing
- **Cloud sync:** End-to-end encryption needed?
- **Voice recording:** Clear privacy policy required

### Hardware Limitations
- **Viture camera:** 5fps max, may limit video quality
- **GPS accuracy:** Indoor use may have poor/no GPS signal
- **Battery:** Camera + GPS + screen = high power draw
- **Storage:** Photos + notes can accumulate quickly

### UX Decisions
- **Default layout:** What's best for first-time users?
- **Gesture controls:** Needed for glasses-only operation?
- **Voice commands:** Essential or nice-to-have?
- **Notification strategy:** When to show toasts vs silent operation?

### Future Hardware Support
- **Other AR glasses:** Support for Xreal, Rokid, etc.?
- **Multiple cameras:** Some glasses have dual cameras
- **Better sensors:** IMU, depth sensors, etc.

---

## Success Metrics (Future Analytics)

### User Engagement
- Daily active users
- Average session length
- Notes/photos created per session
- Feature usage breakdown

### Performance
- Crash rate
- Average battery drain
- Camera connection success rate
- Display targeting accuracy

### User Satisfaction
- App store ratings
- Feature requests
- Bug reports
- User retention

---

## Contributing & Development

### Code Style
- **Language:** Kotlin
- **Architecture:** Single Activity + Fragments (future)
- **Coroutines:** For async operations
- **ViewBinding:** For UI references

### Git Workflow
- **Main branch:** Stable releases
- **Dev branch:** Active development
- **Feature branches:** One per major feature

### Version Numbering
- **v1.x:** MVP and core features
- **v2.x:** Advanced features (voice, AR, etc.)
- **v3.x:** Major rewrites or architecture changes

---

## Support & Documentation

### User Documentation
- [ ] Setup guide (✅ Done)
- [ ] Feature tutorials
- [ ] FAQ
- [ ] Troubleshooting guide
- [ ] Video demos

### Developer Documentation
- [ ] API documentation
- [ ] Architecture overview
- [ ] Contributing guidelines
- [ ] Code style guide

---

## Contact & Feedback

**Developer:** [Your name/handle]
**Repository:** viture-utilities
**Issues:** [Issue tracker URL]
**Discussions:** [Forum/Discord/etc.]

---

## Changelog

### v1.0 (Current - MVP)
- Initial release
- DeX display targeting
- USB camera access (1920x1080)
- Photo capture with timestamps
- Basic text editor
- Black transparency theme

### v1.1 (Planned)
- File management for notes
- Auto-save functionality
- Word count tracker
- Keyboard shortcuts
- Export options

### v1.2 (Planned)
- **Location tagging on photos** 🆕
- Photo gallery view
- Enhanced EXIF metadata
- Manual camera controls

### v1.3 (Planned)
- Google Maps navigation overlay
- Turn-by-turn instructions
- Voice guidance

---

## License

[To be determined - suggest MIT or Apache 2.0]

---

**Last Updated:** 2026-01-14
**Document Version:** 1.0
**Project Status:** MVP Complete, Testing Phase
