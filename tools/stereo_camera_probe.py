#!/usr/bin/env python3
"""
VITURE Luma Ultra Stereo Camera Probe Tool

This tool probes the stereo B&W cameras on the VITURE glasses via USB.
Based on reverse engineering of libcarina_vio.so from SpaceWalker APK.

Protocol:
- USB Device: VID 0x35CA, PID 0x1101
- Command Endpoint: EP 0x04 (OUT, Bulk)
- Data Endpoint: EP 0x85 (IN, Bulk)
- Command Size: 13 bytes (or 5 bytes for some commands)
- Timeout: 1500ms

Usage:
    sudo python3 stereo_camera_probe.py

Requirements:
    pip install pyusb
"""

import sys
import argparse
import time

try:
    import usb.core
    import usb.util
except ImportError:
    print("Error: pyusb not installed. Run: pip install pyusb")
    sys.exit(1)


# VITURE XR Glasses USB identifiers
VITURE_VID = 0x35CA
VITURE_PID = 0x1101

# Endpoints discovered from libcarina_vio.so
EP_COMMAND_OUT = 0x04  # Bulk OUT - send commands
EP_DATA_IN = 0x85      # Bulk IN - receive data

# Timeouts (ms)
TIMEOUT_CMD = 1500
TIMEOUT_READ = 1500

# Command magic bytes discovered from disassembly
# At 0x951ecc-0x951ef0 in libcarina_vio.so:
#   Bytes 0-1: 0xFA55 (little endian of 0x55FA)
#   Byte 2: 0xEA (command type)
#   Bytes 3-4: 0x0008 (little endian 2048, but stored as 0x0800 at offset 3)
#   Byte 5: 0x01 (flag)
#   Byte 6: camera ID/parameter

class VitureStereoCameraProbe:
    """Probe tool for VITURE stereo cameras."""

    def __init__(self):
        self.device = None
        self.interface = None

    def find_device(self):
        """Find and open the VITURE XR Glasses device."""
        self.device = usb.core.find(idVendor=VITURE_VID, idProduct=VITURE_PID)

        if self.device is None:
            print(f"Error: VITURE device not found (VID={VITURE_VID:04x}, PID={VITURE_PID:04x})")
            print("Make sure glasses are connected via USB.")
            return False

        print(f"Found VITURE device: {self.device}")
        print(f"  Manufacturer: {self.device.manufacturer}")
        print(f"  Product: {self.device.product}")
        print(f"  Serial: {self.device.serial_number}")

        return True

    def claim_interface(self, interface_num=0):
        """Claim the USB interface for communication."""
        try:
            # Detach kernel driver if attached
            if self.device.is_kernel_driver_active(interface_num):
                print(f"Detaching kernel driver from interface {interface_num}")
                self.device.detach_kernel_driver(interface_num)

            # Set configuration (usually 1)
            try:
                self.device.set_configuration()
            except usb.core.USBError:
                pass  # May already be configured

            # Claim the interface
            usb.util.claim_interface(self.device, interface_num)
            self.interface = interface_num
            print(f"Claimed interface {interface_num}")
            return True

        except usb.core.USBError as e:
            print(f"Error claiming interface: {e}")
            return False

    def release(self):
        """Release the USB interface."""
        if self.device and self.interface is not None:
            try:
                usb.util.release_interface(self.device, self.interface)
                print(f"Released interface {self.interface}")
            except:
                pass

    def list_endpoints(self):
        """List all endpoints on the device."""
        print("\nDevice Endpoints:")
        for cfg in self.device:
            print(f"  Configuration {cfg.bConfigurationValue}:")
            for intf in cfg:
                print(f"    Interface {intf.bInterfaceNumber}, Alt {intf.bAlternateSetting}:")
                for ep in intf:
                    direction = "IN" if usb.util.endpoint_direction(ep.bEndpointAddress) == usb.util.ENDPOINT_IN else "OUT"
                    ep_type = {
                        usb.util.ENDPOINT_TYPE_BULK: "Bulk",
                        usb.util.ENDPOINT_TYPE_INTR: "Interrupt",
                        usb.util.ENDPOINT_TYPE_ISO: "Isochronous",
                        usb.util.ENDPOINT_TYPE_CTRL: "Control"
                    }.get(usb.util.endpoint_type(ep.bmAttributes), "Unknown")
                    print(f"      EP 0x{ep.bEndpointAddress:02x} ({direction}, {ep_type}) - Max packet: {ep.wMaxPacketSize}")

    def build_stereo_command(self, cmd_type=0xEA, size_param=0x0800, flag=0x01, camera_id=0x00):
        """
        Build a stereo camera command packet (13 bytes).

        Structure discovered from libcarina_vio.so:
          Bytes 0-1: Magic (0xFA55 little endian = 0x55FA)
          Byte 2: Command type (0xEA)
          Bytes 3-4: Size parameter (0x0800 = 2048)
          Byte 5: Flag (0x01)
          Byte 6: Camera ID or parameter
          Bytes 7-12: Reserved/zeros
        """
        cmd = bytearray(13)

        # Magic bytes (0x55FA stored as little endian)
        cmd[0] = 0xFA
        cmd[1] = 0x55

        # Command type
        cmd[2] = cmd_type

        # Size parameter (little endian)
        cmd[3] = size_param & 0xFF
        cmd[4] = (size_param >> 8) & 0xFF

        # Flag
        cmd[5] = flag

        # Camera ID / parameter
        cmd[6] = camera_id

        # Bytes 7-12 are zeros (already initialized)

        return bytes(cmd)

    def build_short_command(self, byte0, byte1, byte2, byte3, byte4):
        """Build a 5-byte command (also observed in libcarina_vio.so)."""
        return bytes([byte0, byte1, byte2, byte3, byte4])

    def send_command(self, cmd_data, timeout=TIMEOUT_CMD):
        """Send a command to EP 0x04."""
        try:
            bytes_written = self.device.write(EP_COMMAND_OUT, cmd_data, timeout=timeout)
            print(f"Sent {bytes_written} bytes to EP 0x{EP_COMMAND_OUT:02x}: {cmd_data.hex()}")
            return bytes_written
        except usb.core.USBError as e:
            print(f"Error sending command: {e}")
            return -1

    def read_response(self, size=256, timeout=TIMEOUT_READ):
        """Read response from EP 0x85."""
        try:
            data = self.device.read(EP_DATA_IN, size, timeout=timeout)
            print(f"Received {len(data)} bytes from EP 0x{EP_DATA_IN:02x}")
            return bytes(data)
        except usb.core.USBError as e:
            if "timeout" in str(e).lower():
                print(f"Read timeout (no response in {timeout}ms)")
            else:
                print(f"Error reading response: {e}")
            return None

    def probe_stereo_cameras(self):
        """Probe the stereo cameras with the discovered command structure."""
        print("\n=== Probing Stereo Cameras ===")

        # Command discovered from disassembly:
        # 0xFA55 magic, 0xEA cmd, 0x0800 size, 0x01 flag, 0x00 camera
        cmd = self.build_stereo_command(
            cmd_type=0xEA,
            size_param=0x0800,  # 2048
            flag=0x01,
            camera_id=0x00
        )

        print(f"\nSending stereo camera probe command:")
        print(f"  Command bytes: {cmd.hex()}")
        print(f"  Breakdown: magic=0x{cmd[1]:02x}{cmd[0]:02x}, cmd=0x{cmd[2]:02x}, "
              f"size=0x{cmd[4]:02x}{cmd[3]:02x}, flag=0x{cmd[5]:02x}, cam=0x{cmd[6]:02x}")

        result = self.send_command(cmd)
        if result < 0:
            return False

        # Try to read response
        print("\nWaiting for response...")
        response = self.read_response(size=256, timeout=TIMEOUT_READ)

        if response:
            print(f"\nResponse ({len(response)} bytes):")
            self.print_hex_dump(response)
            return True
        else:
            print("No response received")
            return False

    def probe_various_commands(self):
        """Try various command variations to discover the protocol."""
        print("\n=== Probing Various Commands ===")

        # List of command variations to try
        test_commands = [
            # (description, cmd_type, size_param, flag, camera_id)
            ("Standard init (from disasm)", 0xEA, 0x0800, 0x01, 0x00),
            ("Camera 0 query", 0xEA, 0x0800, 0x01, 0x00),
            ("Camera 1 query", 0xEA, 0x0800, 0x01, 0x01),
            ("No flag", 0xEA, 0x0800, 0x00, 0x00),
            ("Different cmd type 0x01", 0x01, 0x0800, 0x01, 0x00),
            ("Different cmd type 0x00", 0x00, 0x0800, 0x01, 0x00),
            ("Small size", 0xEA, 0x0040, 0x01, 0x00),
        ]

        for desc, cmd_type, size_param, flag, camera_id in test_commands:
            print(f"\n--- Testing: {desc} ---")
            cmd = self.build_stereo_command(cmd_type, size_param, flag, camera_id)
            print(f"Command: {cmd.hex()}")

            result = self.send_command(cmd)
            if result < 0:
                print("Send failed, skipping...")
                continue

            time.sleep(0.1)  # Small delay

            response = self.read_response(size=512, timeout=500)
            if response:
                print(f"Got response ({len(response)} bytes):")
                self.print_hex_dump(response[:64])  # First 64 bytes
                if len(response) > 64:
                    print(f"  ... ({len(response) - 64} more bytes)")
            else:
                print("No response")

            time.sleep(0.2)  # Delay between commands

    def print_hex_dump(self, data, bytes_per_line=16):
        """Print a hex dump of data."""
        for i in range(0, len(data), bytes_per_line):
            chunk = data[i:i+bytes_per_line]
            hex_str = ' '.join(f'{b:02x}' for b in chunk)
            ascii_str = ''.join(chr(b) if 32 <= b < 127 else '.' for b in chunk)
            print(f"  {i:04x}: {hex_str:<{bytes_per_line*3}}  {ascii_str}")


def main():
    parser = argparse.ArgumentParser(description="VITURE Stereo Camera Probe Tool")
    parser.add_argument('--list-endpoints', '-l', action='store_true',
                        help="List all USB endpoints")
    parser.add_argument('--probe', '-p', action='store_true',
                        help="Probe stereo cameras with discovered command")
    parser.add_argument('--scan', '-s', action='store_true',
                        help="Scan with various command variations")
    parser.add_argument('--interface', '-i', type=int, default=0,
                        help="USB interface number (default: 0)")
    args = parser.parse_args()

    probe = VitureStereoCameraProbe()

    # Find device
    if not probe.find_device():
        return 1

    # List endpoints if requested
    if args.list_endpoints:
        probe.list_endpoints()
        return 0

    # Claim interface
    if not probe.claim_interface(args.interface):
        return 1

    try:
        if args.scan:
            probe.probe_various_commands()
        else:
            # Default: basic probe
            probe.probe_stereo_cameras()
    finally:
        probe.release()

    return 0


if __name__ == "__main__":
    sys.exit(main())
