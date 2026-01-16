# VITURE Luma Ultra USB Protocol Documentation

Reverse-engineered from `libglasses.so` (SpaceWalker APK).

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
| EP 0x82 | IN | Bulk | Unknown (returns 64-byte blob) |
| EP 0x83 | IN | Interrupt | Unknown |
| EP 0x04 | OUT | Bulk | Command channel |
| EP 0x85 | IN | Bulk | Unknown |
| EP 0x06 | OUT | Bulk | Command channel 2 |
| EP 0x87 | IN | Bulk | Unknown |

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

## References

- Source: `sw_extracted/lib/arm64-v8a/libglasses.so` (SpaceWalker APK)
- Source: `sw_extracted/lib/arm64-v8a/libcarina_vio.so` (VIO/SLAM library)
- Tools: aarch64-linux-gnu-objdump, strings, nm
