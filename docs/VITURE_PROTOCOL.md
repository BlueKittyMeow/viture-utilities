# VITURE Luma Ultra USB Protocol Documentation

Reverse-engineered from `libglasses.so` and `libcarina_vio.so` (SpaceWalker APK).

## Current Status (January 2026)

| Component | Status | Notes |
|-----------|--------|-------|
| USB Device Detection | ✅ Working | VID 0x35CA, PID 0x1101 |
| FA 55 Protocol | ✅ Decoded | Both 5-byte and 13-byte formats |
| Serial Number | ✅ Retrieved | P6SPIH53200369 (via 0xED) |
| Device Model | ✅ Retrieved | P6SPIH (via 0xE5) |
| Stereo Cameras | 🔄 **Breakthrough!** | 0x95 triggers EP 0x82 data stream |
| Stereo Frame Format | 🔄 Analyzing | aa8f header, 16KB packets, L/R pairs |
| IMU Access | ✅ Working | Via HID API + EP 0x82 (a2c4 header) |
| UVC Camera | ✅ Working | Standard USB Video Class |

### Recent Findings

- **🎉 BREAKTHROUGH**: Command **0x95** triggers camera data on **EP 0x82**!
- **Stereo frame format discovered**: `aa8f` header, 16KB packets, left/right camera pairs
- **16 working commands total**: 8 in 0x90-0x9F range + 8 in 0xE0-0xEF range
- **0xEA is NOT the camera command** - 0x95 is the key trigger
- **EP 0x82 multiplexes** IMU data (a2c4 header) and camera frames (aa8f header)
- **Camera requires periodic triggering** - 0x95 must be re-sent every ~0.3s

## Hardware Characteristics

**Important**: The VITURE Luma Ultra glasses are **passive displays** with no internal battery:
- All power comes from the USB-C connection
- No power button or internal power source
- Display activates immediately when USB power is provided
- If glasses don't respond, they're not receiving power or USB negotiation failed

### Known Issue: Main Interface Stopped Enumerating (January 2026)

During stereo camera protocol research, the main 35ca:1101 interface stopped responding.

**dmesg output when connected:**
```
usb 3-3: New USB device found, idVendor=1a86, idProduct=8091  # USB Hub - OK
usb 3-3.2: New USB device found, idVendor=0c45, idProduct=636b # Sonix Camera - OK
usb 3-3.3: New USB device found, idVendor=35ca, idProduct=1102 # Microphone - OK
# MISSING: 35ca:1101 XR GLASSES - NOT APPEARING!
```

**What's working:**
- ✅ Internal USB hub (1a86:8091) - 4 ports detected
- ✅ Sonix UVC Camera (0c45:636b) - Fully functional
- ✅ VITURE Microphone (35ca:1102) - Enumerates (HID issues but present)

**What's broken:**
- ❌ **35ca:1101 XR GLASSES** - Main proprietary interface not enumerating
- ❌ No display output (requires 35ca:1101 for DP Alt Mode negotiation?)
- ❌ No IMU/sensor access

**Diagnosis:** The glasses are NOT dead. Power, hub, camera, and mic all work.
Only the main USB controller firmware for 35ca:1101 is stuck/crashed.

**What we were doing when this happened:**
1. Running `--capture` command which sends initialization sequence (0x91-0x98)
2. Running `--uvc` command which:
   - Tries alternate interface settings (0-3) via `set_interface_altsetting()`
   - Sends 0x95 command repeatedly
   - Reads from EP 0x82

**Potentially problematic commands sent:**
- `FA 55 91 00 08 01 00...` through `FA 55 98 00 08 01 00...` (init sequence)
- `FA 55 95 00 08 01 00...` (camera trigger - sent repeatedly)
- `set_interface_altsetting()` calls on interface 0

**Theory:** One of these commands may have put the 35ca:1101 controller into a
state where it won't enumerate. The firmware may need a full power cycle or
there may be a "recovery" command sequence.

**Recovery attempts:**
- [x] Different USB cable - No change
- [x] Different USB port - No change
- [x] SpaceWalker app on phone - Does NOT see glasses either
- [ ] Try on completely different computer (Windows/Mac)
- [ ] Contact VITURE support - likely need firmware recovery procedure

**Note:** Capacitor drain unlikely to help - these are passive displays with no
significant power storage. The 35ca:1101 controller firmware is likely corrupted
or in a locked state that persists in flash memory.

**Confirmed working via ffplay:**
- Sonix camera (/dev/video2) streams video normally
- Internal USB hub functional
- Only 35ca:1101 is affected

## USB Device Overview

The VITURE Luma Ultra presents as 3 USB devices:

| VID:PID | Description | Type | Status |
|---------|-------------|------|--------|
| 35ca:1101 | XR GLASSES | Proprietary (7 endpoints) | Under investigation |
| 0c45:636b | Sonix Camera | Standard UVC | ✓ Works |
| 35ca:1102 | Microphone | USB Audio + HID | Untested |

## Proprietary Interface (0x35ca:0x1101)

### Endpoints

| Endpoint | Direction | Type | Purpose |
|----------|-----------|------|---------|
| EP 0x81 | IN | Interrupt | IMU data (via HID) |
| EP 0x82 | IN | Bulk | **Multiplexed: IMU (a2c4) + Camera frames (aa8f)** |
| EP 0x83 | IN | Interrupt | Unknown |
| EP 0x04 | OUT | Bulk | FA 55 command channel |
| EP 0x85 | IN | Bulk | FA 55 command responses/ACKs |
| EP 0x06 | OUT | Bulk | FF FD command channel |
| EP 0x87 | IN | Bulk | FF FD heartbeat (512-byte status packets) |

## IMU Access

### Method: HID API (NOT raw USB)

The VitureDeviceProvider uses HIDAPI to access IMU data:

```c
// Open HID device
hid_device* handle = hid_libusb_wrap_sys_device(fd, 0);

// Read IMU data (64 bytes, blocking)
int bytes = hid_read_timeout(handle, buffer, 64, -1);

// Process response
UsbResponseProcessor::ProcessResponse(buffer, bytes);
```

### IMU Packet Types

| Type | Size | Description |
|------|------|-------------|
| 0x0307 | 46 bytes | IMU data format 1 |
| 0x0309 | 46 bytes | IMU data format 2 |

## Control Protocol

### Packet Structure

Commands use a framed packet format:

```
Offset | Size | Field
-------|------|------
0-1    | 2    | Magic: 0xFEFF (little endian)
2-3    | 2    | CRC-16
4-5    | 2    | Length = payload_len + 12 (LE)
6-13   | 8    | Reserved (zeros)
14-15  | 2    | Command ID (little endian)
16-17  | 2    | Reserved (zeros)
18+    | N    | Payload data
```

### CRC-16 Algorithm

- **Type**: CRC-16-CCITT
- **Polynomial**: 0x1021
- **Initial Value**: 0x0000
- **Input Reflected**: No
- **Output Reflected**: No

```c
uint16_t calculate_crc(const uint8_t* data, uint16_t len) {
    uint16_t crc = 0;
    for (int i = 0; i < len; i++) {
        uint8_t index = (data[i] ^ (crc >> 8)) & 0xFF;
        crc = CRC_TABLE[index] ^ (crc << 8);
    }
    return crc;
}
```

### Building a Command Packet

```c
void build_command(uint16_t cmd_id, const uint8_t* payload, uint16_t payload_len,
                   uint8_t* output, uint16_t* output_len) {
    // Magic header
    output[0] = 0xFF;
    output[1] = 0xFE;

    // Length field (payload + 12)
    uint16_t length = payload_len + 12;
    output[4] = length & 0xFF;
    output[5] = (length >> 8) & 0xFF;

    // Zeros at 6-13
    memset(&output[6], 0, 8);

    // Command ID
    output[14] = cmd_id & 0xFF;
    output[15] = (cmd_id >> 8) & 0xFF;

    // Zeros at 16-17
    output[16] = 0;
    output[17] = 0;

    // Payload
    if (payload && payload_len > 0) {
        memcpy(&output[18], payload, payload_len);
    }

    // Calculate CRC over bytes 4 to end
    uint16_t crc = calculate_crc(&output[4], length + 2);
    output[2] = crc & 0xFF;
    output[3] = (crc >> 8) & 0xFF;

    *output_len = 18 + payload_len;
}
```

## SDK Architecture

### Key Classes

| Class | Purpose |
|-------|---------|
| `XRDeviceProvider` | Abstract base for device providers |
| `VitureDeviceProvider` | Pure IMU provider (uses HID) |
| `CarinaDeviceProvider` | SLAM/VIO provider (uses Carina A1088 chip) |
| `UsbControlProtocol` | Command send/receive management |
| `UsbProtocolBuilder` | Builds command packets |
| `UsbResponseProcessor` | Parses response packets |
| `UsbImuDataParser` | Parses IMU data (0x0307, 0x0309) |

### Initialization Flow

```
xr_device_provider_create()
    └── Creates VitureDeviceProvider or CarinaDeviceProvider

xr_device_provider_initialize()
    └── UsbControlProtocol::Start()
        └── Creates monitoring thread
        └── Thread reads from USB continuously

xr_device_provider_start()
    └── VitureDeviceProvider::Start()
        └── hid_libusb_wrap_sys_device()  // Open HID
        └── Creates ImuReadThread
        └── Thread calls hid_read_timeout() in loop
```

## Stereo Cameras

**Status**: Protocol discovered via `libcarina_vio.so` analysis.

The stereo B&W cameras are accessed via the same `0x35ca:0x1101` interface, but
require the Carina VIO library for initialization and streaming.

### USB Endpoints for Stereo Cameras

| Endpoint | Direction | Purpose |
|----------|-----------|---------|
| EP 0x04 | OUT (Bulk) | Command channel - send 13-byte commands |
| EP 0x85 | IN (Bulk) | Camera data - stereo frame responses |

### Stereo Camera Access Flow

From `libcarina_vio.so` disassembly:

```c
// 1. Send command to EP 0x04 (13 bytes, 1500ms timeout)
libusb_bulk_transfer(handle, 0x04, cmd_buffer, 13, &transferred, 1500);

// 2. Read response from EP 0x85 (1500ms timeout)
libusb_bulk_transfer(handle, 0x85, data_buffer, buffer_size, &transferred, 1500);
```

### Carina VIO API Functions

| Function | Purpose |
|----------|---------|
| `carina_a1088_viture_init` | Initialize VITURE device (VID=0x35CA, PID=0x1101) |
| `carina_a1088_viture_start` | Start stereo camera streaming |
| `carina_a1088_viture_resume` | Resume streaming after pause |
| `carina_vio_feed_images2` | Feed stereo image pair to VIO |
| `carina_vio_feed_images4` | Feed 4 images (stereo + extra?) |
| `carina_vio_feed_imu` | Feed IMU data to VIO |
| `carina_set_vstframe_callback` | Set callback for Video See-Through frames |
| `carina_a1088_get_cam_param` | Get camera calibration parameters |

### Stereo Camera Initialization (from `carina_a1088_viture_init`)

```c
// Hardcoded device identifiers found in disassembly:
uint16_t vid = 0x35CA;  // VITURE
uint16_t pid = 0x1101;  // XR Glasses

// Function signature (approximate):
int carina_a1088_viture_init(
    const char* config_path,    // x0 - path string
    const char* param2,         // x1 - another path?
    int param3,                 // w2
    int param4,                 // w3
    int param5                  // w4 (on stack)
);
```

### Key Libraries

| Library | Size | Purpose |
|---------|------|---------|
| `libglasses.so` | 1.1 MB | SDK wrapper, HID for IMU |
| `libcarina_vio.so` | 20 MB | VIO/SLAM, stereo camera access via libusb |

### OpenCV Functions Used

The VIO library uses OpenCV for stereo processing:
- `cv::fisheye::stereoRectify` - Stereo rectification for fisheye lenses
- `cv::stereoRectify` - Standard stereo rectification
- ORB feature extraction for SLAM

### Next Steps for Stereo Camera Access

1. **Use libusb directly** to communicate with EP 0x04/0x85
2. **Reverse engineer the 13-byte command structure** sent to EP 0x04
3. **Parse the response frames** from EP 0x85
4. **Alternative**: Load `libcarina_vio.so` and call the API directly

### Stereo Camera Command Structure (EP 0x04)

From disassembly of `libcarina_vio.so` at address `0x951ecc-0x951ef0`:

**13-byte Command Format:**

```
Offset | Size | Field        | Example Value
-------|------|--------------|---------------
0-1    | 2    | Magic        | 0xFA55 (LE) = 0x55FA
2      | 1    | Command type | 0xEA
3-4    | 2    | Size param   | 0x0008 (LE) = 2048
5      | 1    | Flag         | 0x01
6      | 1    | Camera ID    | 0x00 or 0x01
7-12   | 6    | Reserved     | 0x00 (zeros)
```

**Assembly Evidence:**

```asm
951ecc:   mov  w8, #0x55fa              // Magic value
951ed0:   mov  w9, #0xea                // Command type
951ed4:   mov  w10, #0x800              // Size = 2048
951ed8:   mov  w11, #0x1                // Flag
...
951ee4:   strh w8, [x19]                // Store magic at offset 0
951ee8:   strb w9, [x19, #2]            // Store cmd at offset 2
951eec:   sturh w10, [x19, #3]          // Store size at offset 3
951ef0:   strb w11, [x19, #5]           // Store flag at offset 5
```

**5-byte Short Command Format:**

Some commands use a shorter 5-byte format (observed at `0x952448`):

```
Offset | Size | Field
-------|------|------
0-4    | 5    | Command bytes (structure TBD)
```

### Additional Carina API Functions

| Function | Purpose |
|----------|---------|
| `carina_a1088_get_sn` | Get device serial number |
| `carina_a1088_send_custom_data` | Send custom data to device |
| `carina_a1088_read_custom_data` | Read custom data from device |
| `carina_a1088_switch_display_mode` | Switch display mode |
| `carina_a1088_reset_pose` | Reset VIO pose |
| `carina_a1088_get_config_des` | Get configuration descriptor |

### Probe Tool

A Python probe tool is available at `tools/stereo_camera_probe.py`:

```bash
# List USB endpoints
sudo python3 tools/stereo_camera_probe.py --list-endpoints

# Probe stereo cameras with discovered command
sudo python3 tools/stereo_camera_probe.py --probe

# Scan with various command variations
sudo python3 tools/stereo_camera_probe.py --scan
```

**Requirements:**
- pyusb: `pip install pyusb`
- libusb backend installed
- Root/sudo access for USB

## Discovered USB Communication Flow

From disassembly at `0x951ecc-0x951f5c` in `libcarina_vio.so`:

### Step 1: Clear Pending Data
```c
// Read from EP 0x85 to clear any pending data (100ms timeout)
libusb_bulk_transfer(handle, 0x85, buffer, 256, &transferred, 100);
```

### Step 2: Send 13-byte Command
```c
// Send command to EP 0x04 (1500ms timeout)
uint8_t cmd[13] = {
    0xFA, 0x55,     // Magic (0x55FA little-endian)
    0xEA,           // Command type
    0x00, 0x08,     // Size param (0x0800 = 2048 LE)
    0x01,           // Flag (1 = enabled)
    camera_id,      // 0x00 or 0x01
    0, 0, 0, 0, 0, 0  // Reserved
};
libusb_bulk_transfer(handle, 0x04, cmd, 13, &transferred, 1500);
```

### Step 3: Read Response
```c
// Read response from EP 0x85 (1500ms timeout)
libusb_bulk_transfer(handle, 0x85, response, size, &transferred, 1500);

// Response structure:
// Byte 3: Status code (0 = success, 0x71 = specific error)
// Bytes 4-5: Data length (16-bit, needs byte swap)
// Bytes 6+: Actual data
```

### Two Protocol Families Discovered

| Protocol | Magic | EP OUT | EP IN | Purpose |
|----------|-------|--------|-------|---------|
| FA 55 | 0x55FA | 0x04 | 0x85 | Control commands (camera init, config) |
| FF FD | 0xFDFF | 0x06 | 0x87 | Status/heartbeat stream |

### EP 0x82: IMU/Sensor Data

EP 0x82 returns 64-byte packets with sensor data (likely IMU):
```
Sample: a2c4c2d7fc3bec44be7b1443dbdda337...
```
This appears to be floating-point sensor values.

### EP 0x87: Status Heartbeat

EP 0x87 constantly streams 512-byte packets:
```
ff fd XX XX 0d 00 00 00 00 00 YY YY YY 00 00 00 00 00 02 00
│  │  │     │                 │                       │
│  │  │     │                 │                       └── Mode (0x02)
│  │  │     │                 └── Counter (timestamp)
│  │  │     └── Constant 0x0D
│  │  └── CRC/checksum
└──└── Magic: 0xFDFF
```

## Probe Tool Options

```bash
# List USB endpoints
sudo python3 tools/stereo_camera_probe.py --list-endpoints

# Basic probe with FA55 command
sudo python3 tools/stereo_camera_probe.py --probe

# Scan various command variations
sudo python3 tools/stereo_camera_probe.py --scan

# Explore EP 0x87 heartbeat stream
sudo python3 tools/stereo_camera_probe.py --ep87

# Probe FF FD commands on EP 0x06
sudo python3 tools/stereo_camera_probe.py --ep06

# Extended probe (read all endpoints for N seconds)
sudo python3 tools/stereo_camera_probe.py --extended 10
```

**Requirements:**
- pyusb: `pip install pyusb`
- libusb backend installed
- Root/sudo access for USB

## Disassembly Analysis Locations

| File | Location | Content |
|------|----------|---------|
| `~/sw_extracted/lib/arm64-v8a/libcarina_vio.so` | 20MB | VIO/SLAM library |
| `~/sw_extracted/lib/arm64-v8a/libglasses.so` | 1.1MB | SDK wrapper |
| `/tmp/glasses.asm` | 7.8MB | Disassembly output |

### Key Disassembly Addresses

| Address | Function/Purpose |
|---------|------------------|
| `0x8af4c0` | `carina_a1088_viture_init` - Device initialization |
| `0x8af7d0` | `carina_a1088_viture_start` - Start stereo cameras (size 0xa40) |
| `0x951ecc` | USB command building (FA55 magic, 0xEA cmd) |
| `0x951f04` | First USB read from EP 0x85 (256 bytes, 100ms) |
| `0x951f4c` | USB write to EP 0x04 (13-byte command, 1500ms) |
| `0x951f8c` | Second USB read from EP 0x85 (response, 1500ms) |
| `0x99ece0` | `jcx_trigger_cam_start` - Uses command 0x95 |
| `0x94edcc` | `jcx_update_custom_config` - Contains 0xEA sending logic |
| `0x9a00d8` | `send_cus_data` - Low-level USB send function |

### Disassembly Commands

```bash
# Full disassembly of carina_vio.so
aarch64-linux-gnu-objdump -d ~/sw_extracted/lib/arm64-v8a/libcarina_vio.so > /tmp/carina.asm

# Search for specific address range
sed -n '/951e00/,/9520/p' /tmp/carina.asm

# Find exported functions
nm -D ~/sw_extracted/lib/arm64-v8a/libcarina_vio.so | grep viture

# Find libusb function imports
nm -D ~/sw_extracted/lib/arm64-v8a/libcarina_vio.so | grep libusb
```

## Command Formats Discovered

### 13-byte Command Format (Original)

The primary command format discovered from disassembly at `0x951ecc`:

```
Offset | Size | Field        | Example Value
-------|------|--------------|---------------
0-1    | 2    | Magic        | 0xFA 0x55
2      | 1    | Command type | 0xEA
3-4    | 2    | Size param   | 0x00 0x08 (LE = 2048)
5      | 1    | Flag         | 0x01
6      | 1    | Camera ID    | 0x00 or 0x01
7-12   | 6    | Reserved     | 0x00 (zeros)
```

### 5-byte Command Format (Simplified)

**NEW**: Discovered from disassembly at `0x9563ec`. Some commands use a shorter format:

```
Offset | Size | Field
-------|------|------
0      | 1    | Magic: 0xFA
1      | 1    | Magic: 0x55
2      | 1    | Command type
3      | 1    | Param (usually 0x00)
4      | 1    | Param (usually 0x00)
```

**Assembly Evidence** (at `0x9563ec`):
```asm
9563ec:   mov  w8, #0x55                 // 0x55
9563f0:   strb w8, [sp, #105]            // Store at offset 1
9563f4:   mov  w8, #0xed                 // Command type 0xED
9563f8:   strb w8, [sp, #106]            // Store at offset 2
9563fc:   mov  w8, #0xfa                 // 0xFA
956400:   strb wzr, [sp, #107]           // Store 0x00 at offset 3
956404:   strb wzr, [sp, #108]           // Store 0x00 at offset 4
956408:   strb w8, [sp, #104]            // Store 0xFA at offset 0
```

### Response Format

All responses follow the same structure:

```
Offset | Size | Field         | Description
-------|------|---------------|------------
0      | 1    | Magic         | 0xFA
1      | 1    | Magic         | 0x55
2      | 1    | Command Echo  | Same as sent command type
3      | 1    | Status        | 0x00 = success, 0x01 = not ready, 0x08 = error
4-5    | 2    | Data Length   | Little-endian 16-bit length
6+     | N    | Data          | Payload (if status = 0x00)
```

## Status Codes

| Code | Meaning | Action |
|------|---------|--------|
| 0x00 | Success | Data follows in response |
| 0x01 | Not ready / Needs init | Camera may need initialization sequence |
| 0x05 | Unknown error | Returned by 0xE2 |
| 0x06 | State error | 0xE4 returns this after certain commands - device state changed |
| 0x08 | Configuration error | Command may require different parameters |

## Working Command Types (Verified)

### Full Command Scan Results (0xE0-0xEF, 5-byte format)

| Command | Status | Data Len | Purpose/Notes |
|---------|--------|----------|---------------|
| **0xE0** | 0x00 ✓ | 0 | Unknown - success with no data |
| **0xE1** | 0x00 ✓ | 0 | Unknown - success with no data |
| 0xE2 | 0x05 | 0 | Unknown error |
| 0xE3 | 0x01 | 0 | Not ready |
| **0xE4** | 0x00/0x06 | 39729 | Data buffer - state dependent |
| **0xE5** | 0x00 ✓ | 2048 | Device model info ("P6SPIH") |
| **0xE6** | 0x00 ✓ | 0 | Unknown - success with no data |
| **0xE7** | 0x00 ✓ | 0 | Unknown - success with no data |
| **0xE8** | 0x00 ✓ | 0 | Unknown - success with no data |
| 0xE9 | 0x01 | 0 | Not ready |
| 0xEA | 0x01 | 0 | **Camera start** - needs init |
| 0xEB | 0x01 | 0 | Not ready |
| 0xEC | 0x08 | 0 | Configuration error |
| **0xED** | 0x00 ✓ | 16384 | Serial number ("P6SPIH53200369") |
| **0xEE** | 0x00 ✓ | 0 | Enable/init command |
| 0xEF | 0x01 | 0 | Not ready |

**Working commands**: 0xE0, 0xE1, 0xE5, 0xE6, 0xE7, 0xE8, 0xED, 0xEE

### Commands That Return Status=0x01 (Not Ready)

| Command | Format | Status | Notes |
|---------|--------|--------|-------|
| 0xE3 | 5-byte | 0x01 | Not ready |
| 0xE9 | 5-byte | 0x01 | Not ready |
| 0xEA | Both | 0x01 | Camera start - **needs initialization sequence first** |
| 0xEB | 5-byte | 0x01 | Not ready |
| 0xEF | 5-byte | 0x01 | Not ready |
| 0x01 | 5-byte | 0x01 | Potential init command |
| 0x02 | 5-byte | 0x01 | Potential start command |

### Commands That Return Other Errors

| Command | Format | Status | Notes |
|---------|--------|--------|-------|
| 0xE2 | 5-byte | 0x05 | Unknown error |
| 0xE4 | 5-byte | 0x06 | State error (after other commands) |
| 0xEC | 5-byte | 0x08 | Configuration error - may need parameters |

## Device Information Discovered

**Device Serial Number**: `P6SPIH53200369`
- Retrieved via 0xED command
- Format: Model prefix (P6SPIH) + Serial (53200369)

**Device Model**: `P6SPIH`
- Retrieved via 0xE5 command
- Identifies as VITURE Luma Ultra stereo camera module

## Stereo Camera Initialization (Work in Progress)

### Current Understanding

The stereo B&W cameras require an initialization sequence before the 0xEA camera start
command will return status=0x00. The 0xE4 command reports `data_len=39729` but this
appears to be **buffer capacity**, not actual available data.

### Hypothesis: Required Sequence

Based on which commands return status=0x00, the initialization sequence may be:

```
1. 0xEE (enable) -> returns status=0x00
2. 0xE5 (query device) -> returns status=0x00
3. 0xEA (start camera) -> currently returns 0x01, should return 0x00 after init
4. 0xE4 (read data) -> should contain actual camera data after camera started
```

### Sequences Tested (All Failed to Enable 0xEA)

| Sequence | Commands | Result |
|----------|----------|--------|
| Enable then start | 0xEE → 0xEA | ❌ 0xEA still returns 0x01 |
| Enable, start, read | 0xEE → 0xEA → 0xE4 | ❌ 0xEA returns 0x01 |
| Full init | 0xEE → 0xE5 → 0xEA | ❌ 0xEA returns 0x01 |
| Query then start | 0xE5 → 0xEA | ❌ 0xEA returns 0x01 |
| Serial then start | 0xED → 0xEA | ❌ 0xEA returns 0x01 |

### 0xE5 Device Info Response Analysis

The 0xE5 command returns 8 bytes of device info:
```
Response: 22 0e 50 36 53 50 49 48
          │  │  └──────────────── ASCII: "P6SPIH" (model)
          └──┴── 0x0e22 = 3618 (firmware version? device ID?)
```

### Key Observations

1. **0xE4 state changes**: Returns 0x00 on first call, then 0x06 after other commands
2. **0xEE returns 0x00**: This 5-byte command succeeds, possibly enabling hardware
3. **0xEA blocked**: Camera start command consistently returns 0x01 even after ALL working commands
4. **EP 0x87 is heartbeat only**: Returns 512-byte status packets, no camera data

### Exhaustive Testing Results

All of the following sequences were tested - **ALL failed** to enable 0xEA:

- Individual commands: 0xE0, 0xE1, 0xE5, 0xE6, 0xE7, 0xE8, 0xED, 0xEE → 0xEA
- Combinations: 0xE0+0xE1, 0xE6+0xE7, 0xE0+0xEE, 0xE8+0xEE → 0xEA
- Full sequence: 0xE0 → 0xE1 → 0xE5 → 0xE6 → 0xE7 → 0xE8 → 0xEE → 0xEA ❌
- 0xEE with various parameters (0x00, 0x01, 0x08) → 0xEA ❌
- EP 0x06 (FFFD protocol) commands → no effect on FA55 protocol

### Conclusion: Camera Needs VIO Library

The stereo cameras likely require initialization through the **libcarina_vio.so** library
itself, not just USB commands. Possible reasons:

1. **Firmware loading**: The library may load camera firmware during `carina_a1088_viture_init`
2. **Complex state machine**: The init may involve timing-sensitive multi-step processes
3. **Internal configuration**: Camera calibration/parameters may be set programmatically

### New Discovery: Command 0x95 and 0x90-0x9F Range

Deep disassembly of `jcx_trigger_cam_start` (at `0x99ece0`) reveals command **0x95** (149 decimal)
being used as part of the camera start sequence:

```asm
99edd4:   528012a1   mov  w1, #0x95     // command 0x95 loaded
99edd8:   97ff0421   bl   95fe5c        // call internal helper
...
99efe0:   528012a0   mov  w0, #0x95     // command 0x95 again
99efec:   97ff0360   bl   95fd6c        // another internal call
```

### Working Commands in 0x90-0x9F Range (Verified)

Testing with `--discover` revealed **8 working commands**:

| Command | Status | Notes |
|---------|--------|-------|
| **0x90** | 0x00 ✓ | Works |
| 0x91 | timeout | Not responsive |
| **0x92** | 0x00 ✓ | Works |
| 0x93 | timeout | Not responsive |
| **0x94** | 0x00 ✓ | Works |
| **0x95** | 0x00 ✓ | **Key command from disassembly** |
| **0x96** | 0x00 ✓ | Works |
| **0x97** | 0x00 ✓ | Works |
| **0x98** | 0x00 ✓ | Works |
| **0x99** | 0x00 ✓ | Works |
| 0x9A-0x9F | timeout | Not responsive |

**Critical Finding**: Even after sending ALL 16 working commands (8 in 0x90-0x9F + 8 in 0xE0-0xEF),
the 0xEA camera start command still returns status=0x01 (not ready).

### BREAKTHROUGH: EP 0x82 Streams Camera Data After 0x95!

Testing with `--uvc` revealed that **EP 0x82 produces substantial data** after sending command 0x95:

```
=== Exploring EP 0x82 (potential video bulk endpoint) ===
Sending 0x95 then reading from EP 0x82...
  Read 1:    64 bytes, header: a2c4c2d5fc3becc4...  (IMU data)
  Read 2:    64 bytes, header: a2c4c2d5fc3becc4...  (IMU data)
  ...
  Read 17: 16384 bytes, header: aa8f00120f00f43f...  ** FRAME DATA! **
  Read 18: 16384 bytes, header: aa8f006600006800...
  Read 19: 16384 bytes, header: aa8f00121000f43f...
  Read 20: 16384 bytes, header: aa8f00221000f43f...
  Total from EP 0x82: 66,560 bytes
```

**Key Findings:**
- **Two packet types on EP 0x82:**
  - `a2c4` header: 64-byte IMU/sensor data (known)
  - `aa8f` header: **16,384-byte frame packets** (NEW!)

- **Frame packet structure** (confirmed from capture analysis):
  ```
  Offset | Size | Field              | Example Values
  -------|------|--------------------|-----------------
  0-1    | 2    | Magic              | 0xaa8f (always)
  2-3    | 2    | Packet ID          | 0x0012=left seq2, 0x0022=right seq2
  4-5    | 2    | Frame number (LE)  | 17, 18, 19... (incrementing)
  6-7    | 2    | Payload info       | 0x3ff4 (16372) or 0x30d8 (12504)
  8      | 1    | Camera flag        | 0xfc=LEFT, 0xfd=RIGHT
  9-11   | 3    | Unknown            | 0x9c9900
  12-15  | 4    | Data pattern       | Varies per packet
  16+    | N    | Payload data       | ~16KB per packet
  ```

- **Stereo Camera Identification**:
  - **Left camera**: Packet ID 0x1X, camera flag 0xfc
  - **Right camera**: Packet ID 0x2X, camera flag 0xfd
  - Frames come in **pairs** with same frame number

- **Data Characteristics**:
  - Packet size: Exactly 16,384 bytes (16KB)
  - Data rate: ~33 KB/s with periodic 0x95 triggers
  - Stream stops after ~20 packets without re-triggering
  - Payload contains `04 04 04...` patterns (possibly calibration/config data)

### Revised Understanding

The stereo cameras work differently than initially assumed:

| Original Assumption | Reality |
|---------------------|---------|
| 0xEA starts cameras | 0xEA not needed for data flow |
| Camera data on EP 0x85 | Camera data on **EP 0x82** |
| Single stream | Multiplexed: IMU (a2c4) + Frame (aa8f) |
| Continuous streaming | Burst mode - requires periodic 0x95 |

**Key Discoveries:**
- **0x95 triggers EP 0x82 output** - Must be re-sent every ~0.3s to maintain stream
- **EP 0x85 is for command ACKs only** - No camera data here
- **EP 0x82 multiplexes two data types:**
  - `a2c4` header: 64-byte IMU/sensor packets
  - `aa8f` header: 16KB camera frame packets
- **Stereo pairs**: Left (0xfc) and Right (0xfd) cameras send paired frames

### Next Steps (Updated)

1. **✅ Capture EP 0x82 data**: Successfully capturing with `--capture` option
2. **🔄 Decode `aa8f` frame format**: Determine if 16KB packets are:
   - Complete low-res frames (128x128 = 16384 bytes exactly!)
   - Chunks of larger frames (need reassembly)
   - Calibration/config data (unlikely to be actual video)
3. **Analyze payload content**: The `04 04 04...` pattern needs investigation
4. **Test continuous streaming**: Updated probe re-sends 0x95 every 0.3s
5. **Compare with SpaceWalker**: USB trace on Android to verify protocol

**Capture command:**
```bash
sudo python3 tools/stereo_camera_probe.py --capture 10
```

## Probe Tool Commands

```bash
# List USB endpoints
sudo python3 tools/stereo_camera_probe.py --list-endpoints

# Run exact disassembly sequence with all discovered commands
# Includes: 0xE0-0xEF scan, 0xEE parameter variations, init sequences
sudo python3 tools/stereo_camera_probe.py --exact

# Test discovered initialization (0x95 cmd, 0x90-0x9F scan, combinations)
sudo python3 tools/stereo_camera_probe.py --discover

# Explore UVC interfaces, alternate settings, EP 0x82
# Tests: interface classes, alt settings, 0x95 parameters, 0x00-0x20 scan
sudo python3 tools/stereo_camera_probe.py --uvc

# ** NEW ** Capture EP 0x82 stream after 0x95 command
# Saves raw data to /tmp/viture_ep82_capture.bin
sudo python3 tools/stereo_camera_probe.py --capture 10   # capture for 10 seconds

# Try USB control transfers (vendor-specific initialization)
sudo python3 tools/stereo_camera_probe.py --ctrl

# Explore EP 0x87 heartbeat stream
sudo python3 tools/stereo_camera_probe.py --ep87

# Probe EP 0x06 (FF FD protocol)
sudo python3 tools/stereo_camera_probe.py --ep06

# Extended probe (read all endpoints for N seconds)
sudo python3 tools/stereo_camera_probe.py --extended 10

# Basic camera probe
sudo python3 tools/stereo_camera_probe.py --probe
```

## Raw Probe Output Examples

### Successful 0xED Response (Serial Number)

```
Command: 0xED (get serial number) - 5-byte format
Sent: fa 55 ed 00 00
Response: fa 55 ed 00 10 00 50 36 53 50 49 48 35 33 32 30 30 33 36 39 00 00
         │  │  │  │  │     └── ASCII: "P6SPIH53200369"
         │  │  │  │  └── data_len = 0x0010 = 16 bytes
         │  │  │  └── status = 0x00 (success)
         │  │  └── cmd echo = 0xED
         │  └── magic 0x55
         └── magic 0xFA
```

### Blocked 0xEA Response (Camera Not Ready)

```
Command: 0xEA (camera start)
Sent: fa 55 ea 00 08 01 00 00 00 00 00 00 00
Response: fa 55 ea 01 00 00
                   │
                   └── status = 0x01 (not ready)
```

## References

- Source: `~/sw_extracted/lib/arm64-v8a/libglasses.so` (SpaceWalker APK)
- Source: `~/sw_extracted/lib/arm64-v8a/libcarina_vio.so` (VIO/SLAM library)
- Tools: aarch64-linux-gnu-objdump, strings, nm
