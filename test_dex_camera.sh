#!/bin/bash
# Test script for Viture camera access in DeX mode

echo "=== Viture DeX Camera Test ==="
echo ""

# Check adb connection
echo "1. Checking ADB connection..."
adb devices -l
echo ""

read -p "Is your phone listed above? (y/n): " connected
if [ "$connected" != "y" ]; then
    echo "Please enable USB debugging on your phone and connect it."
    echo "Settings → Developer Options → USB Debugging"
    exit 1
fi

echo ""
echo "2. Checking video devices on phone..."
adb shell "ls -la /dev/video*" 2>&1
echo ""

echo "3. Checking USB devices on phone..."
adb shell "ls -la /dev/bus/usb/*" 2>&1 | head -20
echo ""

echo "4. Looking for Viture camera (vendor 0x35ca)..."
adb shell "cat /sys/class/video4linux/video*/name" 2>&1
echo ""

echo "=== Instructions ==="
echo ""
echo "Now connect your Viture glasses to your phone via USB-C"
echo "and enable DeX mode if it doesn't start automatically."
echo ""
read -p "Press Enter when DeX is running..."

echo ""
echo "5. Re-checking video devices after glasses connected..."
adb shell "ls -la /dev/video*" 2>&1
echo ""

echo "6. Checking for new USB devices..."
adb shell "lsusb" 2>&1 || echo "(lsusb not available on this device)"
echo ""

echo "7. Trying to get video device info..."
adb shell "v4l2-ctl --list-devices" 2>&1 || echo "(v4l2-ctl not available)"
echo ""

echo "8. Checking USB camera driver messages..."
adb shell "dmesg | grep -i 'video\|camera\|uvc\|usb'" 2>&1 | tail -30
echo ""

echo "=== Test Complete ==="
echo ""
echo "If you see /dev/video* devices appear after connecting glasses,"
echo "we can try to capture from them next!"
