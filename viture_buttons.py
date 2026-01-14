#!/usr/bin/env python3
import usb.core
import usb.util
import sys

# Find VITURE glasses
dev = usb.core.find(idVendor=0x35ca, idProduct=0x1101)
if dev is None:
    print("VITURE glasses not found!")
    sys.exit(1)

print(f"Found: {dev.manufacturer} - {dev.product}")

# Detach kernel driver if attached
if dev.is_kernel_driver_active(0):
    dev.detach_kernel_driver(0)

# Set configuration
dev.set_configuration()

# Try reading from interrupt endpoint 0x83 (most likely for events)
print("\nListening for button events on EP 0x83...")
print("Press buttons on your glasses! (Ctrl+C to stop)\n")

while True:
    try:
        # Read from endpoint 0x83, timeout 1000ms
        data = dev.read(0x83, 1024, timeout=1000)
        if len(data) > 0:
            print(f"Data: {data.tobytes().hex()}")
            print(f"  Raw: {list(data[:20])}...")  # First 20 bytes
    except usb.core.USBTimeoutError:
        pass  # No data, keep waiting
    except KeyboardInterrupt:
        print("\nStopped.")
        break
    except Exception as e:
        print(f"Error: {e}")
        break