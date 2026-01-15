# Viture Camera Access Test Results - DeX Mode

**Date:** 2026-01-14
**Phone:** Samsung Galaxy S22 Ultra (SM-S908U1)
**Glasses:** Viture Luma Pro
**Test Mode:** Samsung DeX via USB-C

## Summary

⚠️ **USB camera access via kernel (V4L2) does NOT work**
✅ **USB camera access via userspace (USB Host API) DOES work**

**IMPORTANT:** This document originally tested only kernel-level access. Subsequent testing with CameraFi app proved that userspace UVC access IS viable. See "CameraFi Validation" section below.

## Detailed Findings

### USB Detection ✓
The phone successfully detects both Viture USB devices:
- `35ca:1101` - Viture glasses (main device)
- `35ca:1102` - Viture microphone device

### Video Device Detection ✗
The Viture cameras do NOT appear as `/dev/video*` devices:
- Phone has 6 video devices (built-in cameras)
- No new video devices appear when glasses are connected
- Video devices remain: video0, video1, video2, video3, video32, video33
- video2/video3 are a different USB camera (vendor 0c45, not Viture)

### Root Cause (Kernel-Level Access)
Android (including DeX mode) does not expose USB cameras at the kernel level. The OS:
1. Sees the Viture glasses on the USB bus ✓
2. Does NOT load the UVC (USB Video Class) kernel driver ✗
3. Does NOT create `/dev/video*` device nodes for the cameras ✗
4. Camera2 API only works with built-in phone cameras ✗

## CameraFi Validation ✅

**Test Date:** 2026-01-14 (after initial V4L2 testing)
**App:** CameraFi (from Play Store)
**Result:** ✅ **SUCCESS - Camera accessible!**

### What Worked
- CameraFi detected Viture camera as "USB 2.0 Camera"
- Camera preview displayed (though at low resolution due to app settings)
- Proves that **userspace UVC access works** in DeX mode
- No root required, no kernel modifications needed

### Technical Explanation
CameraFi uses **Android USB Host API** to communicate with USB cameras:
- Bypasses kernel-level drivers
- Direct userspace communication with USB device
- Implements UVC protocol in app layer
- This is the approach our VitureHUD app will use

### Implications for Our Project
✅ **Camera access IS possible** via USB Host API + UVC library
✅ **DeX mode is NOT a blocker** for our implementation
✅ **Userspace approach is proven** and viable
❌ **Kernel approach (V4L2) remains blocked** (but we don't need it)

## Implications

### What Works in DeX Mode
✓ Display output (HDMI/DisplayPort alt mode)
✓ Touch input (if supported)
✓ USB OTG devices (keyboards, mice, storage)
✓ **Viture camera access via USB Host API + UVC library** ⭐
✓ **Userspace UVC apps (CameraFi, our VitureHUD app)**

### What Doesn't Work in DeX Mode
✗ Kernel-level camera access (`/dev/video*` devices)
✗ V4L2 API for USB cameras
✗ Camera2 API for external cameras (only works with built-in cameras)

## Implementation Approaches

### Option 1: Native Android App ⭐ RECOMMENDED
**Use USB Host API + UVCCamera library directly on phone**

- **Status:** ✅ Proven viable by CameraFi
- **Approach:** VitureHUD Android app (already in development)
- **Pros:**
  - No additional hardware needed
  - Truly portable (just phone + glasses)
  - Works in DeX mode
  - No root required
- **Cons:**
  - Requires UVCCamera library integration
  - JitPack dependencies failed (using local build)
- **Implementation:** See `docs/CAMERA_INTEGRATION_PLAN.md`

### Option 2: Linux-Based Portable Device (ALTERNATIVE)
Use a small Linux computer as a "bridge" device (no longer necessary, but still viable):

**Hardware Options:**
- Raspberry Pi 4 or Pi 5 (~$75)
- Orange Pi 5 (~$90, more powerful)
- Mini PC (Intel N100, ~$150)

**Setup:**
```
Phone (DeX) ← WiFi hotspot → Pi ← USB-C → Viture Glasses
                                    ↓
                              Camera + Display
```

**Workflow:**
1. Pi runs HUD application (pygame/GTK)
2. Pi captures from Viture camera via UVC
3. Pi hosts web server for remote control
4. Phone browser controls Pi HUD
5. Can also mirror phone screen via scrcpy

**Benefits:**
- Full camera access (proven working on your laptop)
- Pocket-sized and portable
- Can run for hours on USB power bank
- No phone modifications needed

### Option 3: Laptop-Based (Current Setup)
Keep glasses connected to laptop:
- ✓ Full camera access
- ✓ Full HUD control
- ✗ Less portable

### Option 4: Wait for Viture Firmware
Viture has mentioned camera API access in future firmware:
- Timeline unknown
- May require specific app integration
- May not work in DeX mode anyway

## Recommendations

### ⭐ PRIMARY RECOMMENDATION: Native Android App
**Proceed with VitureHUD Android app development:**

1. **Implementation:**
   - Follow `docs/CAMERA_INTEGRATION_PLAN.md`
   - Clone and build UVCCamera library locally
   - Integrate with existing VitureHUD app
   - Use USB Host API for camera access

2. **Timeline:**
   - Estimated: 8-13 hours implementation
   - Result: Fully portable, phone-only solution
   - No additional hardware needed

3. **Status:** Ready to implement (plan complete)

### ALTERNATIVE: Raspberry Pi-Based System
**If native Android approach fails or additional features needed:**

Build a **Raspberry Pi-based system**:

1. **Hardware:**
   - Raspberry Pi 4 (4GB RAM) or Pi 5
   - USB-C power delivery adapter
   - 20,000mAh power bank
   - Small case (pocket-sized)

2. **Software:**
   - Raspberry Pi OS Lite (headless)
   - Python HUD app (adapt existing code)
   - Flask web server for phone control
   - scrcpy server for phone mirroring

3. **Features:**
   - Camera capture with button/timer
   - Text editor for notes/novel writing
   - Navigation overlay (scrape Google Maps)
   - Phone screen mirror in HUD
   - WiFi control interface

4. **Cost:** ~$100-150 total

### For Testing/Development
Continue using laptop setup to develop HUD features, then port to Pi when ready.

## Next Steps

1. **Decide on approach:**
   - Pi-based portable system? (recommended)
   - Laptop-based for now?
   - Wait for Viture firmware?

2. **If going Pi route:**
   - Purchase hardware
   - Set up headless Pi with WiFi
   - Port HUD code to Pi
   - Build phone control interface
   - Add navigation overlay

3. **If staying with laptop:**
   - Enhance current HUD
   - Add camera capture button
   - Build text editor
   - Test navigation overlay

Let me know which direction you'd like to pursue!
