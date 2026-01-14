# viture-utilities

Utilities and tools for working with Viture Luma Pro AR glasses on Linux.

## Components

### HUD Applications

**hud_pygame.py** - Simple HUD overlay using pygame
- Displays a borderless fullscreen window on the Viture glasses (HDMI display)
- Text input area with green terminal-style text
- Red quit button in top-left corner
- Press ESC to exit

**hud_test.py** - HUD overlay using GTK3
- Alternative implementation with transparent background support
- Positioned text area in bottom-left corner

### Button Event Listeners

**viture_buttons.py** - USB button event monitor (device 0x1101)
- Listens for button presses via USB interrupt endpoint 0x83
- Requires pyusb (`pip install pyusb`)

**viture_buttons2.py** - Alternative button monitor (device 0x1102 - microphone)
- Attempts to read from HID interfaces on microphone device
- Tests multiple endpoints (0x82, 0x84, 0x86)

## Setup

### Display Configuration

The Viture glasses should appear as a second HDMI display. Check with:
```bash
xrandr --listmonitors
```

The HUD apps are configured to display on the second monitor (HDMI-1) at position 1920x0.

### Dependencies

```bash
pip install pygame pyusb
# For GTK version:
sudo apt install python3-gi python3-gi-cairo gir1.2-gtk-3.0
```

### USB Permissions

For button event monitoring, you may need USB permissions:
```bash
# Add udev rule for Viture devices (vendor ID 0x35ca)
echo 'SUBSYSTEM=="usb", ATTRS{idVendor}=="35ca", MODE="0666"' | sudo tee /etc/udev/rules.d/99-viture.rules
sudo udevadm control --reload-rules
```

## Usage

```bash
# Run the pygame HUD
python3 hud_pygame.py

# Run the GTK HUD
python3 hud_test.py

# Monitor button events
python3 viture_buttons.py
# or
python3 viture_buttons2.py
```

## Notes

- Black areas (`#000000`) appear transparent on the Viture glasses
- The HUD apps position themselves on the second display automatically
- Button monitoring is experimental and may require adjusting USB endpoints/devices
