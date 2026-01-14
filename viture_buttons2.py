#!/usr/bin/env python3
import usb.core
import usb.util
import sys

# Find VITURE microphone device (has the HID interfaces)
dev = usb.core.find(idVendor=0x35ca, idProduct=0x1102)
if dev is None:
    print("VITURE microphone device not found!")
    sys.exit(1)

print(f"Found: {dev.manufacturer} - {dev.product}")

# HID interfaces are 2, 3, 4 - detach kernel drivers
for i in [2, 3, 4]:
    try:
        if dev.is_kernel_driver_active(i):
            dev.detach_kernel_driver(i)
            print(f"Detached kernel driver from interface {i}")
    except Exception as e:
        print(f"Could not detach interface {i}: {e}")

# Claim the HID interfaces
for i in [2, 3, 4]:
    try:
        usb.util.claim_interface(dev, i)
        print(f"Claimed interface {i}")
    except Exception as e:
        print(f"Could not claim interface {i}: {e}")

endpoints = [0x82, 0x84, 0x86]

print("\nListening... Press buttons! (Ctrl+C to stop)")

count = 0
while True:
    try:
        for ep in endpoints:
            try:
                data = dev.read(ep, 64, timeout=50)
                if len(data) > 0:
                    print(f"EP 0x{ep:02x}: {data.tobytes().hex()}")
                    print(f"Raw: {list(data)}")
            except usb.core.USBTimeoutError:
                pass
        
        count += 1
        if count % 20 == 0:
            print(".", end="", flush=True)
            
    except KeyboardInterrupt:
        print("\nStopped.")
        break
    except Exception as e:
        print(f"Error: {e}")
        break