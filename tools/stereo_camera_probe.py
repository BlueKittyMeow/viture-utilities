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
        try:
            print(f"  Manufacturer: {self.device.manufacturer}")
            print(f"  Product: {self.device.product}")
            print(f"  Serial: {self.device.serial_number}")
        except (ValueError, usb.core.USBError) as e:
            print(f"  (Could not read device strings: {e})")
            print("  Device may need to be unplugged and reconnected")

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
            if "timed out" in str(e).lower() or e.errno == 110:
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

    def try_streaming(self, num_reads=10):
        """Send init command then continuously read to look for streaming data."""
        print("\n=== Attempting Streaming Mode ===")

        # First, send the init command
        cmd = self.build_stereo_command(
            cmd_type=0xEA,
            size_param=0x0800,
            flag=0x01,
            camera_id=0x00
        )

        print(f"Sending init command: {cmd.hex()}")
        self.send_command(cmd)

        # Read the ACK
        ack = self.read_response(size=64, timeout=500)
        if ack:
            print(f"ACK: {ack.hex()}")

        # Now try continuous reads to catch any streaming data
        print(f"\nAttempting {num_reads} continuous reads from EP 0x85...")
        total_bytes = 0

        for i in range(num_reads):
            try:
                # Try reading a larger buffer - image data would be bigger
                data = self.device.read(EP_DATA_IN, 4096, timeout=200)
                if data:
                    total_bytes += len(data)
                    print(f"Read {i+1}: {len(data)} bytes")
                    if len(data) > 6:  # More than just an ACK
                        self.print_hex_dump(bytes(data)[:128])
                        if len(data) > 128:
                            print(f"  ... ({len(data) - 128} more bytes)")
            except usb.core.USBError as e:
                if "timeout" not in str(e).lower():
                    print(f"Read {i+1}: Error - {e}")
                # Timeout is expected if no streaming

        print(f"\nTotal bytes received: {total_bytes}")

        # Also try other IN endpoints
        print("\n--- Checking other IN endpoints ---")
        for ep in [0x82, 0x87]:
            print(f"\nTrying EP 0x{ep:02x}...")
            try:
                data = self.device.read(ep, 512, timeout=200)
                if data:
                    print(f"Got {len(data)} bytes from EP 0x{ep:02x}:")
                    self.print_hex_dump(bytes(data)[:64])
            except usb.core.USBError as e:
                if "timed out" in str(e).lower() or e.errno == 110:
                    print(f"  Timeout (no data)")
                else:
                    print(f"  Error: {e}")

    def try_start_commands(self):
        """Try various potential 'start streaming' commands."""
        print("\n=== Trying Potential Start Commands ===")

        # Commands to try based on common patterns
        start_candidates = [
            # 5-byte commands (seen in disassembly)
            ("5-byte: 01 00 00 00 00", bytes([0x01, 0x00, 0x00, 0x00, 0x00])),
            ("5-byte: FA 55 01 00 00", bytes([0xFA, 0x55, 0x01, 0x00, 0x00])),
            ("5-byte: FA 55 EA 01 00", bytes([0xFA, 0x55, 0xEA, 0x01, 0x00])),

            # 13-byte with different cmd types
            ("13-byte: cmd=0x01 (start?)", self.build_stereo_command(0x01, 0x0800, 0x01, 0x00)),
            ("13-byte: cmd=0x02 (start?)", self.build_stereo_command(0x02, 0x0800, 0x01, 0x00)),
            ("13-byte: cmd=0x10 (start?)", self.build_stereo_command(0x10, 0x0800, 0x01, 0x00)),
            ("13-byte: cmd=0x20 (start?)", self.build_stereo_command(0x20, 0x0800, 0x01, 0x00)),
            ("13-byte: cmd=0x71 (seen in code)", self.build_stereo_command(0x71, 0x0800, 0x01, 0x00)),
        ]

        for desc, cmd in start_candidates:
            print(f"\n--- {desc} ---")
            print(f"Command: {cmd.hex()}")

            result = self.send_command(cmd)
            if result < 0:
                continue

            # Try multiple reads
            for _ in range(3):
                response = self.read_response(size=1024, timeout=300)
                if response and len(response) > 6:
                    print(f"Interesting response ({len(response)} bytes):")
                    self.print_hex_dump(response[:128])
                    break
                elif response:
                    print(f"ACK: {response.hex()}")
                    break

            time.sleep(0.1)

    def explore_ep87(self, num_reads=20):
        """Explore EP 0x87 which seems to have structured data."""
        print("\n=== Exploring EP 0x87 (Potential Camera Data) ===")

        # First send init command
        cmd = self.build_stereo_command(0xEA, 0x0800, 0x01, 0x00)
        print(f"Sending init: {cmd.hex()}")
        self.send_command(cmd)

        # Read ACK from EP 0x85
        ack = self.read_response(size=64, timeout=500)
        if ack:
            print(f"ACK from EP 0x85: {ack.hex()}")

        # Now continuously read from EP 0x87
        print(f"\nReading {num_reads} times from EP 0x87...")
        unique_headers = set()
        total_bytes = 0

        for i in range(num_reads):
            try:
                data = self.device.read(0x87, 4096, timeout=100)
                if data:
                    total_bytes += len(data)
                    header = bytes(data[:16]).hex()
                    if header not in unique_headers:
                        unique_headers.add(header)
                        print(f"\nRead {i+1}: {len(data)} bytes - NEW header pattern:")
                        self.print_hex_dump(bytes(data)[:64])
                    else:
                        print(f"Read {i+1}: {len(data)} bytes (same header)")
            except usb.core.USBError as e:
                if "timeout" not in str(e).lower():
                    print(f"Read {i+1}: Error - {e}")

        print(f"\nTotal: {total_bytes} bytes, {len(unique_headers)} unique header patterns")

        # Also try EP 0x06 (second OUT endpoint) - maybe it controls EP 0x87?
        print("\n--- Trying EP 0x06 (second command endpoint) ---")
        test_cmds = [
            bytes([0xFF, 0xFD, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]),
            bytes([0xFF, 0xFD, 0xA2, 0xC0, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]),
        ]
        for cmd in test_cmds:
            print(f"\nSending to EP 0x06: {cmd.hex()}")
            try:
                written = self.device.write(0x06, cmd, timeout=500)
                print(f"Wrote {written} bytes")

                # Try reading response from EP 0x87
                for _ in range(3):
                    try:
                        data = self.device.read(0x87, 512, timeout=200)
                        if data:
                            print(f"Response from EP 0x87 ({len(data)} bytes):")
                            self.print_hex_dump(bytes(data)[:64])
                            break
                    except usb.core.USBError:
                        pass
            except usb.core.USBError as e:
                print(f"Error: {e}")


    def probe_ep06_commands(self):
        """Try various commands on EP 0x06 to find camera start command."""
        print("\n=== Probing EP 0x06 Commands (FF FD Protocol) ===")

        # Build commands with FF FD magic
        def build_fffd_cmd(cmd_type, param1=0, param2=0, param3=0):
            """Build a 13-byte command with FF FD magic."""
            cmd = bytearray(13)
            cmd[0] = 0xFF
            cmd[1] = 0xFD
            cmd[2] = cmd_type
            cmd[3] = param1 & 0xFF
            cmd[4] = (param1 >> 8) & 0xFF
            cmd[5] = param2 & 0xFF
            cmd[6] = param3 & 0xFF
            return bytes(cmd)

        # Commands to try
        test_commands = [
            # Basic command types
            ("cmd=0x01 (init?)", build_fffd_cmd(0x01)),
            ("cmd=0x02 (start?)", build_fffd_cmd(0x02)),
            ("cmd=0x03 (query?)", build_fffd_cmd(0x03)),
            ("cmd=0x10 (enable?)", build_fffd_cmd(0x10)),
            ("cmd=0x11", build_fffd_cmd(0x11)),
            ("cmd=0x20 (stream?)", build_fffd_cmd(0x20)),
            ("cmd=0x21", build_fffd_cmd(0x21)),
            ("cmd=0x30", build_fffd_cmd(0x30)),
            ("cmd=0x40", build_fffd_cmd(0x40)),
            ("cmd=0x50", build_fffd_cmd(0x50)),
            ("cmd=0x60", build_fffd_cmd(0x60)),
            ("cmd=0x70", build_fffd_cmd(0x70)),
            ("cmd=0x80", build_fffd_cmd(0x80)),
            ("cmd=0xA0", build_fffd_cmd(0xA0)),
            ("cmd=0xEA (mirror FA55?)", build_fffd_cmd(0xEA)),

            # With parameters
            ("cmd=0x01, param=0x0800", build_fffd_cmd(0x01, 0x0800)),
            ("cmd=0x02, param=0x0800", build_fffd_cmd(0x02, 0x0800)),
            ("cmd=0x01, param=0x01", build_fffd_cmd(0x01, 0x01)),
            ("cmd=0x02, param=0x01", build_fffd_cmd(0x02, 0x01)),
        ]

        for desc, cmd in test_commands:
            print(f"\n--- {desc} ---")
            print(f"Command: {cmd.hex()}")

            try:
                written = self.device.write(0x06, cmd, timeout=500)
                print(f"Wrote {written} bytes to EP 0x06")

                # Read responses from EP 0x87
                for read_num in range(5):
                    try:
                        data = self.device.read(0x87, 4096, timeout=150)
                        if data:
                            print(f"  Response {read_num+1}: {len(data)} bytes")
                            if len(data) > 20:  # More than just header
                                print(f"  Header: {bytes(data[:20]).hex()}")
                                # Check if it looks like image data
                                non_zero = sum(1 for b in data[20:] if b != 0)
                                if non_zero > 10:
                                    print(f"  ** Contains {non_zero} non-zero bytes after header! **")
                                    self.print_hex_dump(bytes(data)[:128])
                            else:
                                print(f"  Data: {bytes(data).hex()}")
                    except usb.core.USBError:
                        break

            except usb.core.USBError as e:
                print(f"Error: {e}")

            time.sleep(0.05)

        # Try a sequence: init then start
        print("\n\n=== Trying Command Sequences ===")
        sequences = [
            ("Init(0x01) -> Start(0x02)", [build_fffd_cmd(0x01), build_fffd_cmd(0x02)]),
            ("Init(0x10) -> Start(0x11)", [build_fffd_cmd(0x10), build_fffd_cmd(0x11)]),
            ("FA55 init -> FFFD start", [self.build_stereo_command(0xEA, 0x0800, 0x01, 0x00), build_fffd_cmd(0x01)]),
        ]

        for desc, cmds in sequences:
            print(f"\n--- Sequence: {desc} ---")
            for i, cmd in enumerate(cmds):
                ep = 0x04 if cmd[:2] == b'\xfa\x55' else 0x06
                print(f"  Step {i+1}: EP 0x{ep:02x} <- {cmd.hex()}")
                try:
                    self.device.write(ep, cmd, timeout=500)
                except usb.core.USBError as e:
                    print(f"    Error: {e}")
                    continue

                # Read response
                resp_ep = 0x85 if ep == 0x04 else 0x87
                try:
                    data = self.device.read(resp_ep, 4096, timeout=200)
                    if data:
                        print(f"    Response from EP 0x{resp_ep:02x}: {len(data)} bytes")
                        if len(data) > 64:
                            non_zero = sum(1 for b in data[20:] if b != 0)
                            print(f"    Non-zero bytes after header: {non_zero}")
                except usb.core.USBError:
                    pass

                time.sleep(0.05)

    def exact_disasm_sequence(self):
        """Execute the exact sequence from libcarina_vio.so disassembly (0x951ecc-0x951f9c)."""
        print("\n=== Exact Disassembly Sequence (0x951ecc-0x951f9c) ===")
        print("Following the exact USB flow from libcarina_vio.so")

        # Step 1: Clear pending data from EP 0x85 (100ms timeout)
        print("\n--- Step 1: Clear EP 0x85 (100ms timeout) ---")
        try:
            data = self.device.read(0x85, 256, timeout=100)
            if data:
                print(f"Cleared {len(data)} bytes: {bytes(data).hex()}")
        except usb.core.USBError:
            print("No pending data (timeout)")

        # Step 2: Send command for camera 0, then camera 1
        for camera_id in [0, 1]:
            print(f"\n--- Step 2: Send command for camera {camera_id} ---")

            # Build exact command from disassembly
            cmd = bytearray(13)
            cmd[0] = 0xFA
            cmd[1] = 0x55
            cmd[2] = 0xEA  # Command type
            cmd[3] = 0x00  # Size param low
            cmd[4] = 0x08  # Size param high (0x0800 = 2048)
            cmd[5] = 0x01  # Flag (enabled)
            cmd[6] = camera_id
            # bytes 7-12 are zeros

            print(f"Command: {bytes(cmd).hex()}")
            try:
                written = self.device.write(0x04, cmd, timeout=1500)
                print(f"Wrote {written} bytes to EP 0x04")
            except usb.core.USBError as e:
                print(f"Write error: {e}")
                continue

            # Step 3: Read response from EP 0x85 (1500ms timeout)
            print("\n--- Step 3: Read response from EP 0x85 ---")
            try:
                response = self.device.read(0x85, 512, timeout=1500)
                if response:
                    print(f"Response ({len(response)} bytes): {bytes(response).hex()}")

                    # Parse response according to disassembly
                    if len(response) >= 6:
                        magic = (response[1] << 8) | response[0]
                        cmd_echo = response[2]
                        status = response[3]
                        data_len = (response[5] << 8) | response[4]  # May need swap

                        print(f"  Magic: 0x{magic:04x}")
                        print(f"  Cmd echo: 0x{cmd_echo:02x}")
                        print(f"  Status: 0x{status:02x} {'(SUCCESS)' if status == 0 else '(error/no data)'}")
                        print(f"  Data length: {data_len}")

                        if status == 0 and data_len > 0:
                            print(f"  ** Got data! First bytes: {bytes(response[6:min(6+data_len, len(response))]).hex()}")
                        elif status == 0x71:
                            print("  Status 0x71 = specific error code from disassembly")

            except usb.core.USBError as e:
                print(f"Read error: {e}")

            time.sleep(0.1)

        # Try with flag=0 (seen in the loop at 0x952174)
        print("\n--- Trying with flag=0 ---")
        cmd = bytearray(13)
        cmd[0] = 0xFA
        cmd[1] = 0x55
        cmd[2] = 0xEA
        cmd[3] = 0x00
        cmd[4] = 0x08
        cmd[5] = 0x00  # Flag = 0
        cmd[6] = 0x00

        print(f"Command: {bytes(cmd).hex()}")
        try:
            self.device.write(0x04, cmd, timeout=1500)
            response = self.device.read(0x85, 512, timeout=1500)
            if response:
                print(f"Response ({len(response)} bytes): {bytes(response).hex()}")
        except usb.core.USBError as e:
            print(f"Error: {e}")

        # Try 5-byte command format discovered at 0x9563ec
        print("\n--- Trying 5-byte command format (from 0x9563ec disassembly) ---")
        five_byte_cmds = [
            (0xED, "get serial number"),
            (0xE5, "get device info"),
            (0xE4, "get data"),
            (0xEA, "camera command"),
            (0xEB, "potential init"),
            (0xEC, "potential config"),
            (0xEE, "seen at 0x99e544"),
            (0x01, "init"),
            (0x02, "start"),
        ]

        for cmd_type, desc in five_byte_cmds:
            cmd = bytes([0xFA, 0x55, cmd_type, 0x00, 0x00])
            print(f"\n5-byte cmd 0x{cmd_type:02x} ({desc}): {cmd.hex()}")
            try:
                self.device.write(0x04, cmd, timeout=1500)
                response = self.device.read(0x85, 512, timeout=1500)
                if response:
                    print(f"  Response ({len(response)} bytes): {bytes(response).hex()}")
                    if len(response) >= 4:
                        status = response[3] if len(response) > 3 else 0
                        print(f"  Status: 0x{status:02x}")
                        if status == 0 and len(response) > 6:
                            data = bytes(response[6:])
                            print(f"  Data: {data.hex()[:100]}...")
                            try:
                                ascii_str = data.rstrip(b'\x00').decode('ascii', errors='ignore')
                                if ascii_str and len(ascii_str) > 2:
                                    print(f"  ASCII: '{ascii_str}'")
                            except:
                                pass
            except usb.core.USBError as e:
                print(f"  Error: {e}")

        # Try other command types discovered in disassembly (13-byte format)
        print("\n--- Trying 13-byte command format ---")
        other_cmds = [
            (0xE4, "0x9523b8 - data transfer"),
            (0xE5, "0x9526c4 - device info"),
            (0xED, "0x956354 - serial number"),
            (0x01, "potential init"),
            (0x02, "potential start"),
        ]

        for cmd_type, desc in other_cmds:
            cmd = bytearray(13)
            cmd[0] = 0xFA
            cmd[1] = 0x55
            cmd[2] = cmd_type
            cmd[3] = 0x00
            cmd[4] = 0x08
            cmd[5] = 0x01
            cmd[6] = 0x00

            print(f"\nCmd 0x{cmd_type:02x} ({desc}):")
            print(f"  Command: {bytes(cmd).hex()}")
            try:
                self.device.write(0x04, cmd, timeout=1500)
                response = self.device.read(0x85, 512, timeout=1500)
                if response:
                    print(f"  Response ({len(response)} bytes): {bytes(response).hex()}")
                    if len(response) >= 6:
                        status = response[3]
                        data_len = (response[5] << 8) | response[4]
                        print(f"  Status: 0x{status:02x}, Data len: {data_len}")
                        if status == 0 and data_len > 0:
                            data_portion = bytes(response[6:min(6+data_len, len(response))])
                            print(f"  ** SUCCESS! Data ({len(data_portion)} bytes): {data_portion.hex()}")
                            # Try to decode as ASCII if printable
                            try:
                                ascii_str = data_portion.rstrip(b'\x00').decode('ascii', errors='ignore')
                                if ascii_str and all(32 <= ord(c) < 127 for c in ascii_str):
                                    print(f"  ** ASCII: '{ascii_str}'")
                            except:
                                pass
            except usb.core.USBError as e:
                print(f"  Error: {e}")

        # Scan ALL commands in 0xE0-0xEF range
        print("\n\n--- Scanning ALL commands 0xE0-0xEF ---")
        working_cmds = []
        for cmd_type in range(0xE0, 0xF0):
            cmd = bytes([0xFA, 0x55, cmd_type, 0x00, 0x00])
            try:
                self.device.write(0x04, cmd, timeout=500)
                response = self.device.read(0x85, 512, timeout=500)
                if response and len(response) >= 4:
                    status = response[3]
                    data_len = (response[5] << 8) | response[4] if len(response) >= 6 else 0
                    status_str = {0x00: "OK", 0x01: "NOT_READY", 0x08: "ERROR"}.get(status, f"0x{status:02x}")
                    if status == 0x00:
                        working_cmds.append(cmd_type)
                        print(f"  0x{cmd_type:02X}: {status_str}, len={data_len} ** WORKS **")
                    else:
                        print(f"  0x{cmd_type:02X}: {status_str}, len={data_len}")
            except usb.core.USBError:
                print(f"  0x{cmd_type:02X}: timeout/error")

        print(f"\nWorking commands: {[hex(c) for c in working_cmds]}")

        # Try 0xEE with parameters - it works, maybe params enable camera
        print("\n\n--- Trying 0xEE with various parameters ---")
        ee_params = [
            (0x00, 0x00),  # default
            (0x01, 0x00),  # param1 = 1
            (0x00, 0x01),  # param2 = 1
            (0x01, 0x01),  # both = 1
            (0x00, 0x08),  # size param like 0xEA
            (0x08, 0x00),  # reversed
        ]
        for p1, p2 in ee_params:
            cmd = bytes([0xFA, 0x55, 0xEE, p1, p2])
            print(f"\n0xEE with params ({p1}, {p2}): {cmd.hex()}")
            try:
                self.device.write(0x04, cmd, timeout=500)
                response = self.device.read(0x85, 512, timeout=500)
                if response:
                    status = response[3] if len(response) >= 4 else -1
                    print(f"  Response: {bytes(response).hex()}")
                    print(f"  Status: 0x{status:02x}")

                    # Now immediately try 0xEA
                    cmd_ea = bytes([0xFA, 0x55, 0xEA, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
                    self.device.write(0x04, cmd_ea, timeout=500)
                    resp_ea = self.device.read(0x85, 512, timeout=500)
                    if resp_ea:
                        ea_status = resp_ea[3] if len(resp_ea) >= 4 else -1
                        print(f"  -> 0xEA status: 0x{ea_status:02x} {'** SUCCESS! **' if ea_status == 0 else ''}")
            except usb.core.USBError as e:
                print(f"  Error: {e}")

        # Try initialization sequences
        print("\n\n--- Trying initialization sequences ---")

        sequences = [
            ("0xEE -> 0xEA (enable then start)", [0xEE, 0xEA]),
            ("0xEE -> 0xEA -> 0xE4 (enable, start, read)", [0xEE, 0xEA, 0xE4]),
            ("0xE5 -> 0xEA (query then start)", [0xE5, 0xEA]),
            ("0xED -> 0xEA (serial then start)", [0xED, 0xEA]),
            ("0xEE -> 0xE5 -> 0xEA (full init)", [0xEE, 0xE5, 0xEA]),
            # New sequences using discovered working commands
            ("0xE0 -> 0xEA (E0 first)", [0xE0, 0xEA]),
            ("0xE1 -> 0xEA (E1 first)", [0xE1, 0xEA]),
            ("0xE6 -> 0xEA (E6 first)", [0xE6, 0xEA]),
            ("0xE7 -> 0xEA (E7 first)", [0xE7, 0xEA]),
            ("0xE8 -> 0xEA (E8 first)", [0xE8, 0xEA]),
            ("0xE0 -> 0xE1 -> 0xEA (E0+E1)", [0xE0, 0xE1, 0xEA]),
            ("0xE6 -> 0xE7 -> 0xEA (E6+E7)", [0xE6, 0xE7, 0xEA]),
            ("0xE0 -> 0xEE -> 0xEA (E0+EE)", [0xE0, 0xEE, 0xEA]),
            ("0xE8 -> 0xEE -> 0xEA (E8+EE)", [0xE8, 0xEE, 0xEA]),
            ("All working -> 0xEA", [0xE0, 0xE1, 0xE5, 0xE6, 0xE7, 0xE8, 0xEE, 0xEA]),
        ]

        for desc, cmd_sequence in sequences:
            print(f"\n--- Sequence: {desc} ---")
            last_status = None

            for cmd_type in cmd_sequence:
                # Use 13-byte format
                cmd = bytearray(13)
                cmd[0] = 0xFA
                cmd[1] = 0x55
                cmd[2] = cmd_type
                cmd[3] = 0x00
                cmd[4] = 0x08
                cmd[5] = 0x01
                cmd[6] = 0x00

                try:
                    self.device.write(0x04, cmd, timeout=1500)
                    response = self.device.read(0x85, 512, timeout=1500)
                    if response:
                        status = response[3] if len(response) > 3 else -1
                        data_len = (response[5] << 8) | response[4] if len(response) >= 6 else 0
                        last_status = status
                        status_str = "SUCCESS" if status == 0 else f"error {status}"
                        print(f"  0x{cmd_type:02x}: status=0x{status:02x} ({status_str}), data_len={data_len}")

                        # If this is 0xEA and it succeeded, we found the init sequence!
                        if cmd_type == 0xEA and status == 0:
                            print(f"  ** CAMERA START SUCCEEDED! **")
                except usb.core.USBError as e:
                    print(f"  0x{cmd_type:02x}: Error - {e}")
                    break

                time.sleep(0.05)

            # If last command was 0xEA with success, try reading 0xE4
            if last_status == 0 and cmd_sequence[-1] == 0xEA:
                print("  Trying 0xE4 read after successful sequence...")
                cmd = bytearray(13)
                cmd[0:7] = [0xFA, 0x55, 0xE4, 0x00, 0x08, 0x01, 0x00]
                try:
                    self.device.write(0x04, cmd, timeout=1500)
                    response = self.device.read(0x85, 4096, timeout=500)
                    if response:
                        print(f"  0xE4 response: {len(response)} bytes, first 20: {bytes(response[:20]).hex()}")
                except usb.core.USBError as e:
                    print(f"  0xE4: {e}")

        # Special handling for 0xE4 - read header first, then read full data based on length
        print("\n\n--- Special: 0xE4 with extended read (potential camera data) ---")
        cmd = bytearray(13)
        cmd[0] = 0xFA
        cmd[1] = 0x55
        cmd[2] = 0xE4
        cmd[3] = 0x00
        cmd[4] = 0x08
        cmd[5] = 0x01
        cmd[6] = 0x00

        print(f"Command: {bytes(cmd).hex()}")
        try:
            self.device.write(0x04, cmd, timeout=1500)

            # First read: get the header with status and data length
            header = self.device.read(0x85, 512, timeout=1500)
            if header and len(header) >= 6:
                print(f"  Header ({len(header)} bytes): {bytes(header).hex()}")
                status = header[3]
                data_len = (header[5] << 8) | header[4]
                print(f"  Status: 0x{status:02x}, Data length: {data_len}")

                if status == 0 and data_len > 0:
                    print(f"\n  ** Reading {data_len} bytes of payload data... **")

                    # Read the actual data payload
                    total_data = bytearray()
                    bytes_remaining = data_len
                    read_count = 0

                    while bytes_remaining > 0 and read_count < 500:
                        try:
                            # Read in chunks
                            chunk_size = min(16384, bytes_remaining + 1000)  # Extra buffer
                            chunk = self.device.read(0x85, chunk_size, timeout=500)
                            if chunk:
                                total_data.extend(chunk)
                                bytes_remaining -= len(chunk)
                                read_count += 1
                                if read_count % 10 == 0:
                                    print(f"  Read {read_count}: {len(total_data)}/{data_len} bytes")
                            else:
                                break
                        except usb.core.USBError as e:
                            if "timed out" in str(e).lower() or e.errno == 110:
                                print(f"  Timeout after {len(total_data)} bytes")
                                break
                            raise

                    print(f"\n  Total payload received: {len(total_data)} bytes (expected {data_len})")

                    if len(total_data) > 0:
                        print(f"  First 128 bytes:")
                        self.print_hex_dump(bytes(total_data[:128]))
                        if len(total_data) > 128:
                            print(f"  Last 64 bytes:")
                            self.print_hex_dump(bytes(total_data[-64:]))

                        # Analyze data pattern
                        non_zero = sum(1 for b in total_data if b != 0)
                        print(f"  Non-zero bytes: {non_zero} / {len(total_data)}")

                        # Check for common image signatures
                        if total_data[:2] == b'\xff\xd8':
                            print("  ** JPEG signature detected! **")
                        elif total_data[:8] == b'\x89PNG\r\n\x1a\n':
                            print("  ** PNG signature detected! **")
                        elif total_data[:4] == b'RIFF':
                            print("  ** RIFF (possibly AVI) detected! **")

                        # Byte frequency analysis
                        freq = {}
                        for b in total_data:
                            freq[b] = freq.get(b, 0) + 1
                        top_bytes = sorted(freq.items(), key=lambda x: -x[1])[:10]
                        print(f"  Top 10 byte values: {[(hex(b), c) for b, c in top_bytes]}")

                        # Save to file for analysis
                        output_file = "/tmp/viture_0xE4_data.bin"
                        with open(output_file, 'wb') as f:
                            f.write(total_data)
                        print(f"\n  ** Data saved to {output_file} **")
                        print(f"  Analyze with: hexdump -C {output_file} | head -100")
                        print(f"  Or check file type: file {output_file}")
                else:
                    print("  No data to read (status != 0 or data_len == 0)")

        except usb.core.USBError as e:
            print(f"  Error: {e}")

    def try_discovered_init(self):
        """
        Try the initialization sequence discovered from deep disassembly.

        Key discoveries from libcarina_vio.so:
        1. jcx_trigger_cam_start uses command 0x95 (149) via internal helper
        2. Before sending 0xEA, the library reads from EP 0x85 first
        3. The 13-byte packet format:
           FA 55 [CMD] [SIZE_LO] [SIZE_HI] [FLAG] [CAM_ID] [00 00 00 00 00 00]
        4. For 0xEA: size=0x0800, flag=0x01
        """
        print("\n=== Testing Discovered Initialization Sequence ===")
        print("Based on deep disassembly of libcarina_vio.so")

        # Step 0: Clear any pending data from EP 0x85
        print("\n--- Step 0: Clear pending data ---")
        try:
            while True:
                data = self.device.read(0x85, 512, timeout=50)
                if data:
                    print(f"  Cleared: {bytes(data).hex()[:40]}...")
                else:
                    break
        except usb.core.USBError:
            print("  No pending data")

        # Step 1: Try command 0x95 discovered in jcx_trigger_cam_start
        print("\n--- Step 1: Command 0x95 (from jcx_trigger_cam_start) ---")
        for format_type in ["5-byte", "13-byte"]:
            if format_type == "5-byte":
                cmd = bytes([0xFA, 0x55, 0x95, 0x00, 0x00])
            else:
                cmd = bytes([0xFA, 0x55, 0x95, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])

            print(f"  {format_type}: {cmd.hex()}")
            try:
                self.device.write(0x04, cmd, timeout=1500)
                response = self.device.read(0x85, 512, timeout=1500)
                if response:
                    status = response[3] if len(response) >= 4 else -1
                    print(f"    Response: {bytes(response).hex()}")
                    print(f"    Status: 0x{status:02x} ({'SUCCESS' if status == 0 else 'error'})")
                    if status == 0:
                        print("    ** 0x95 WORKS! **")
            except usb.core.USBError as e:
                print(f"    Error: {e}")

        # Step 2: Try the exact sequence from 0x951ecc
        print("\n--- Step 2: Exact sequence from 0x951ecc-0x951f9c ---")

        # First: clear read (like at 0x951f04)
        print("  2a: Clear read from EP 0x85 (100ms timeout, 256 bytes)")
        try:
            clear_data = self.device.read(0x85, 256, timeout=100)
            if clear_data:
                print(f"      Got {len(clear_data)} bytes: {bytes(clear_data).hex()}")
        except usb.core.USBError:
            print("      No pending data")

        # Build exact 13-byte command from disassembly
        # At 0x951ecc: mov w8, #0x55fa (stored as FA 55)
        # At 0x951ed0: mov w9, #0xea
        # At 0x951ed4: mov w10, #0x800
        # At 0x951ed8: mov w11, #0x1
        cmd = bytearray(13)
        cmd[0] = 0xFA
        cmd[1] = 0x55
        cmd[2] = 0xEA
        cmd[3] = 0x00  # size low
        cmd[4] = 0x08  # size high (0x0800)
        cmd[5] = 0x01  # flag
        cmd[6] = 0x00  # camera_id

        print(f"  2b: Send command to EP 0x04: {bytes(cmd).hex()}")
        try:
            self.device.write(0x04, cmd, timeout=1500)

            # Read response (like at 0x951f88)
            print("  2c: Read response from EP 0x85 (1500ms timeout)")
            response = self.device.read(0x85, 512, timeout=1500)
            if response and len(response) >= 6:
                status = response[3]
                data_len = (response[5] << 8) | response[4]
                print(f"      Response: {bytes(response).hex()}")
                print(f"      Status: 0x{status:02x}, Data length: {data_len}")

                if status == 0:
                    print("      ** SUCCESS! Camera start accepted! **")
        except usb.core.USBError as e:
            print(f"      Error: {e}")

        # Step 3: Try with flag=0 (seen at 0x952174)
        print("\n--- Step 3: Try with flag=0 (from 0x952174 loop) ---")
        cmd[5] = 0x00  # flag = 0
        print(f"  Command: {bytes(cmd).hex()}")
        try:
            self.device.write(0x04, cmd, timeout=1500)
            response = self.device.read(0x85, 512, timeout=1500)
            if response:
                status = response[3] if len(response) >= 4 else -1
                print(f"  Response: {bytes(response).hex()}")
                print(f"  Status: 0x{status:02x}")
        except usb.core.USBError as e:
            print(f"  Error: {e}")

        # Step 4: Try sequence 0x95 -> 0xEA
        print("\n--- Step 4: Sequence 0x95 -> 0xEA ---")
        for cmd_type in [0x95, 0xEA]:
            cmd = bytes([0xFA, 0x55, cmd_type, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
            try:
                self.device.write(0x04, cmd, timeout=1500)
                response = self.device.read(0x85, 512, timeout=1500)
                if response:
                    status = response[3] if len(response) >= 4 else -1
                    print(f"  0x{cmd_type:02X}: status=0x{status:02x} {'SUCCESS!' if status == 0 else ''}")
            except usb.core.USBError as e:
                print(f"  0x{cmd_type:02X}: Error - {e}")

        # Step 5: Scan 0x90-0x9F range (near 0x95)
        print("\n--- Step 5: Scan 0x90-0x9F command range ---")
        working = []
        for cmd_type in range(0x90, 0xA0):
            cmd = bytes([0xFA, 0x55, cmd_type, 0x00, 0x00])
            try:
                self.device.write(0x04, cmd, timeout=500)
                response = self.device.read(0x85, 512, timeout=500)
                if response and len(response) >= 4:
                    status = response[3]
                    if status == 0:
                        working.append(cmd_type)
                        print(f"  0x{cmd_type:02X}: STATUS=0x00 ** WORKS **")
                    elif status not in [0x01, 0x08]:  # Not typical errors
                        print(f"  0x{cmd_type:02X}: status=0x{status:02x} (unusual)")
            except usb.core.USBError:
                pass

        if working:
            print(f"\nWorking commands in 0x90-0x9F: {[hex(c) for c in working]}")

            # Try each working command followed by 0xEA
            print("\n--- Step 6: Try [working_cmd] -> 0xEA ---")
            for init_cmd in working:
                init = bytes([0xFA, 0x55, init_cmd, 0x00, 0x00])
                ea = bytes([0xFA, 0x55, 0xEA, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])

                try:
                    self.device.write(0x04, init, timeout=500)
                    self.device.read(0x85, 512, timeout=500)

                    self.device.write(0x04, ea, timeout=500)
                    response = self.device.read(0x85, 512, timeout=500)
                    if response:
                        status = response[3] if len(response) >= 4 else -1
                        if status == 0:
                            print(f"  ** 0x{init_cmd:02X} -> 0xEA: SUCCESS! **")
                        else:
                            print(f"  0x{init_cmd:02X} -> 0xEA: status=0x{status:02x}")
                except usb.core.USBError as e:
                    print(f"  0x{init_cmd:02X} -> 0xEA: error - {e}")

        # Step 7: Check what data 0x9X commands return (maybe one IS the camera command)
        print("\n--- Step 7: Check 0x9X command responses in detail ---")
        for cmd_type in [0x90, 0x92, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99]:
            # Try 13-byte format with size parameter
            cmd = bytes([0xFA, 0x55, cmd_type, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
            try:
                self.device.write(0x04, cmd, timeout=1500)
                response = self.device.read(0x85, 4096, timeout=1500)
                if response:
                    status = response[3] if len(response) >= 4 else -1
                    data_len = (response[5] << 8) | response[4] if len(response) >= 6 else 0
                    print(f"  0x{cmd_type:02X}: status=0x{status:02x}, data_len={data_len}")
                    if data_len > 0 and len(response) > 6:
                        data = bytes(response[6:min(6+data_len, len(response))])
                        print(f"       Data: {data.hex()[:60]}...")
                        # Check for ASCII
                        try:
                            ascii_part = data.rstrip(b'\x00').decode('ascii', errors='ignore')
                            if ascii_part and len(ascii_part) > 2:
                                print(f"       ASCII: '{ascii_part[:50]}'")
                        except:
                            pass
            except usb.core.USBError as e:
                print(f"  0x{cmd_type:02X}: error - {e}")

        # Step 8: Try all 0x9X + all 0xEX combinations
        print("\n--- Step 8: Full scan - all 0x9X then 0xEA ---")
        all_9x = [0x90, 0x92, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99]
        all_ex = [0xE0, 0xE1, 0xE5, 0xE6, 0xE7, 0xE8, 0xED, 0xEE]

        # Send all 0x9X first, then all 0xEX, then 0xEA
        print("  Sending all 0x9X commands...")
        for cmd_type in all_9x:
            cmd = bytes([0xFA, 0x55, cmd_type, 0x00, 0x00])
            try:
                self.device.write(0x04, cmd, timeout=500)
                self.device.read(0x85, 512, timeout=500)
            except:
                pass

        print("  Sending all 0xEX commands...")
        for cmd_type in all_ex:
            cmd = bytes([0xFA, 0x55, cmd_type, 0x00, 0x00])
            try:
                self.device.write(0x04, cmd, timeout=500)
                self.device.read(0x85, 512, timeout=500)
            except:
                pass

        print("  Now trying 0xEA...")
        ea = bytes([0xFA, 0x55, 0xEA, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
        try:
            self.device.write(0x04, ea, timeout=1500)
            response = self.device.read(0x85, 512, timeout=1500)
            if response:
                status = response[3] if len(response) >= 4 else -1
                print(f"  0xEA after all commands: status=0x{status:02x} {'** SUCCESS! **' if status == 0 else ''}")
        except usb.core.USBError as e:
            print(f"  0xEA: error - {e}")

        # Step 9: Try reading from EP 0x85 continuously after 0x9X commands
        print("\n--- Step 9: Try continuous reads after 0x95 (camera data?) ---")
        cmd_95 = bytes([0xFA, 0x55, 0x95, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
        try:
            self.device.write(0x04, cmd_95, timeout=1500)
            total_data = 0
            for i in range(10):
                try:
                    data = self.device.read(0x85, 16384, timeout=200)
                    if data:
                        total_data += len(data)
                        if len(data) > 20:
                            print(f"  Read {i+1}: {len(data)} bytes, header: {bytes(data[:20]).hex()}")
                except usb.core.USBError:
                    break
            print(f"  Total from EP 0x85 after 0x95: {total_data} bytes")
        except usb.core.USBError as e:
            print(f"  Error: {e}")

    def explore_uvc_and_alternate(self):
        """
        Explore if the device has UVC (USB Video Class) interfaces or
        alternate settings that might enable camera streaming.

        UVC cameras often have:
        - Interface with class 0x0E (Video)
        - Alternate settings that enable/disable streaming
        - Isochronous endpoints for video data
        """
        print("\n=== Exploring UVC and Alternate Interfaces ===")

        # Check for UVC-related interfaces
        print("\n--- Checking interface classes ---")
        uvc_interfaces = []
        vendor_interfaces = []

        for cfg in self.device:
            for intf in cfg:
                class_code = intf.bInterfaceClass
                subclass = intf.bInterfaceSubClass
                protocol = intf.bInterfaceProtocol

                class_name = {
                    0x00: "Device",
                    0x0E: "VIDEO (UVC)",
                    0x01: "Audio",
                    0x02: "CDC",
                    0x03: "HID",
                    0x08: "Mass Storage",
                    0xFF: "Vendor Specific"
                }.get(class_code, f"Unknown (0x{class_code:02x})")

                print(f"  Interface {intf.bInterfaceNumber}: Class={class_name}, "
                      f"SubClass=0x{subclass:02x}, Protocol=0x{protocol:02x}")

                if class_code == 0x0E:
                    uvc_interfaces.append(intf.bInterfaceNumber)
                    print("    ** UVC INTERFACE FOUND! **")
                elif class_code == 0xFF:
                    vendor_interfaces.append(intf.bInterfaceNumber)

                # List endpoints for each interface
                for ep in intf:
                    direction = "IN" if usb.util.endpoint_direction(ep.bEndpointAddress) == usb.util.ENDPOINT_IN else "OUT"
                    ep_type = {
                        usb.util.ENDPOINT_TYPE_BULK: "Bulk",
                        usb.util.ENDPOINT_TYPE_INTR: "Interrupt",
                        usb.util.ENDPOINT_TYPE_ISO: "Isochronous",
                        usb.util.ENDPOINT_TYPE_CTRL: "Control"
                    }.get(usb.util.endpoint_type(ep.bmAttributes), "Unknown")
                    print(f"      EP 0x{ep.bEndpointAddress:02x} ({direction}, {ep_type}) - MaxPacket: {ep.wMaxPacketSize}")

                    if ep_type == "Isochronous":
                        print("        ** ISOCHRONOUS EP - possible video streaming! **")

        # Try alternate settings on vendor interfaces
        print("\n--- Trying alternate settings ---")
        for intf_num in vendor_interfaces:
            print(f"\nInterface {intf_num} alternate settings:")
            try:
                # Check available alternate settings
                cfg = self.device.get_active_configuration()
                for intf in cfg:
                    if intf.bInterfaceNumber == intf_num:
                        alt = intf.bAlternateSetting
                        print(f"  Found alternate setting {alt}")
            except Exception as e:
                print(f"  Error checking: {e}")

            # Try setting different alternate settings
            for alt_setting in range(4):
                try:
                    self.device.set_interface_altsetting(interface=intf_num, alternate_setting=alt_setting)
                    print(f"  Alt setting {alt_setting}: OK")

                    # After setting alternate, try 0xEA
                    cmd_ea = bytes([0xFA, 0x55, 0xEA, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
                    try:
                        self.device.write(0x04, cmd_ea, timeout=500)
                        resp = self.device.read(0x85, 512, timeout=500)
                        if resp and len(resp) >= 4:
                            status = resp[3]
                            if status == 0:
                                print(f"    ** 0xEA SUCCEEDED after setting alt {alt_setting}! **")
                            else:
                                print(f"    0xEA status: 0x{status:02x}")
                    except usb.core.USBError as e:
                        print(f"    0xEA failed: {e}")

                except usb.core.USBError as e:
                    if "invalid" not in str(e).lower():
                        print(f"  Alt setting {alt_setting}: {e}")

        # Explore EP 0x82 in detail
        print("\n--- Exploring EP 0x82 (potential video bulk endpoint) ---")
        print("Sending 0x95 then reading from EP 0x82...")

        cmd_95 = bytes([0xFA, 0x55, 0x95, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
        try:
            self.device.write(0x04, cmd_95, timeout=1500)
            # Read ACK from EP 0x85
            try:
                self.device.read(0x85, 512, timeout=500)
            except:
                pass

            # Now try EP 0x82
            total_data = 0
            for i in range(20):
                try:
                    data = self.device.read(0x82, 16384, timeout=100)
                    if data:
                        total_data += len(data)
                        if i < 5 or len(data) > 1000:
                            print(f"  Read {i+1}: {len(data)} bytes, first 20: {bytes(data[:20]).hex()}")
                except usb.core.USBError as e:
                    if "timeout" not in str(e).lower():
                        print(f"  Read {i+1}: {e}")
                    break

            print(f"  Total from EP 0x82: {total_data} bytes")

        except usb.core.USBError as e:
            print(f"  Error: {e}")

        # Try different parameters for 0x95
        print("\n--- Testing 0x95 with various parameters ---")
        param_tests = [
            (0x00, 0x00, 0x00, "default"),
            (0x00, 0x08, 0x01, "size=2048, flag=1"),
            (0x00, 0x08, 0x00, "size=2048, flag=0"),
            (0x01, 0x00, 0x00, "param=1"),
            (0x01, 0x00, 0x01, "param=1, flag=1"),
            (0x02, 0x00, 0x00, "param=2 (camera 2?)"),
            (0x00, 0x10, 0x01, "size=4096, flag=1"),
            (0x00, 0x20, 0x01, "size=8192, flag=1"),
        ]

        for p3, p4, p5, desc in param_tests:
            cmd = bytes([0xFA, 0x55, 0x95, p3, p4, p5, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
            print(f"\n  0x95 ({desc}): {cmd.hex()}")
            try:
                self.device.write(0x04, cmd, timeout=1500)
                response = self.device.read(0x85, 4096, timeout=1500)
                if response:
                    status = response[3] if len(response) >= 4 else -1
                    data_len = (response[5] << 8) | response[4] if len(response) >= 6 else 0
                    print(f"    Status: 0x{status:02x}, data_len={data_len}")
                    if data_len > 0 and len(response) > 6:
                        print(f"    Data: {bytes(response[6:min(6+data_len, len(response))]).hex()[:60]}...")

                    # Check for continuous data after
                    extra_reads = 0
                    for _ in range(5):
                        try:
                            extra = self.device.read(0x85, 8192, timeout=100)
                            if extra:
                                extra_reads += len(extra)
                        except:
                            break
                    if extra_reads > 0:
                        print(f"    Additional data after response: {extra_reads} bytes")

            except usb.core.USBError as e:
                print(f"    Error: {e}")

        # Check if 0x9X commands are actually for camera *configuration* not start
        print("\n\n--- Hypothesis: 0x9X commands configure, different cmd starts ---")
        print("Testing: configure with 0x95, then scan for start command...")

        # Send 0x95 first
        cmd_95 = bytes([0xFA, 0x55, 0x95, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
        try:
            self.device.write(0x04, cmd_95, timeout=500)
            self.device.read(0x85, 512, timeout=500)
        except:
            pass

        # Now scan for potential "start" commands we might have missed
        print("\nScanning 0x00-0x20 range after 0x95...")
        for cmd_type in range(0x00, 0x21):
            cmd = bytes([0xFA, 0x55, cmd_type, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
            try:
                self.device.write(0x04, cmd, timeout=300)
                response = self.device.read(0x85, 512, timeout=300)
                if response and len(response) >= 4:
                    status = response[3]
                    data_len = (response[5] << 8) | response[4] if len(response) >= 6 else 0
                    if status == 0:
                        print(f"  0x{cmd_type:02X}: STATUS=0x00, data_len={data_len} ** WORKS **")
                        if data_len > 0:
                            print(f"       Data: {bytes(response[6:]).hex()[:40]}")
            except usb.core.USBError:
                pass

    def capture_ep82_stream(self, duration_seconds=5, output_file="/tmp/viture_ep82_capture.bin"):
        """
        Capture data from EP 0x82 after sending 0x95 command.

        Discovery: EP 0x82 produces large packets (16KB) with 'aa8f' header
        after sending command 0x95. This appears to be camera frame data!

        Frame header structure (16 bytes):
          0-1:  Magic (0xaa8f)
          2-3:  Packet ID - low nibble=seq, high nibble=camera (0x1X=left, 0x2X=right)
          4-5:  Frame number (little endian)
          6-7:  Payload size or timestamp
          8-11: Camera flag (fc9c9900=left, fd9c9900=right)
          12-15: Data pattern
        """
        print(f"\n=== Capturing EP 0x82 Stream for {duration_seconds} seconds ===")

        # CRITICAL: Initialize camera subsystem with 0x9X commands first
        print("\n--- Initializing camera subsystem ---")
        init_cmds = [0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98]
        for cmd_type in init_cmds:
            cmd = bytes([0xFA, 0x55, cmd_type, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
            try:
                self.device.write(0x04, cmd, timeout=500)
                response = self.device.read(0x85, 512, timeout=500)
                if response and len(response) >= 4:
                    status = response[3]
                    print(f"  0x{cmd_type:02X}: status=0x{status:02x}")
            except usb.core.USBError as e:
                if "timed out" not in str(e).lower() and e.errno != 110:
                    print(f"  0x{cmd_type:02X}: {e}")
                else:
                    print(f"  0x{cmd_type:02X}: no response")

        # Try alternate interface settings (may be required to enable streaming)
        print("\n--- Trying alternate interface settings ---")
        for alt_setting in range(4):
            try:
                self.device.set_interface_altsetting(interface=0, alternate_setting=alt_setting)
                print(f"  Alt setting {alt_setting}: OK")
            except usb.core.USBError as e:
                if "invalid" not in str(e).lower():
                    print(f"  Alt setting {alt_setting}: {e}")

        # Send initial 0x95 trigger and read from EP 0x82 to prime the stream
        print("\n--- Priming camera stream ---")
        cmd_95 = bytes([0xFA, 0x55, 0x95, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
        try:
            self.device.write(0x04, cmd_95, timeout=1500)
            # Read ACK from EP 0x85 with longer timeout
            try:
                ack = self.device.read(0x85, 512, timeout=500)
                if ack and len(ack) >= 4:
                    print(f"  0x95 ACK: status=0x{ack[3]:02x}")
            except:
                pass

            # Initial read burst from EP 0x82 to see if data is flowing
            primed = False
            for i in range(10):
                try:
                    data = self.device.read(0x82, 16384, timeout=100)
                    if data and len(data) > 0:
                        print(f"  Priming read {i+1}: {len(data)} bytes, header: {bytes(data[:8]).hex()}")
                        primed = True
                except:
                    pass

            if primed:
                print("  Stream primed successfully!")
            else:
                print("  Warning: No data during priming - stream may not be active")
        except usb.core.USBError as e:
            print(f"  Priming error: {e}")

        print("\nStarting capture...")
        print("Will re-send 0x95 every 0.3s to maintain streaming")

        # Capture from EP 0x82
        print(f"\nCapturing from EP 0x82 for {duration_seconds} seconds...")
        print(f"Output file: {output_file}")

        all_data = bytearray()
        packet_info = []  # (offset, size, header_hex)
        start_time = time.time()
        read_count = 0
        aa8f_packets = 0
        a2c4_packets = 0
        other_packets = 0
        timeout_count = 0
        last_trigger_time = 0
        cmd_95 = bytes([0xFA, 0x55, 0x95, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])

        # Track frame pairs
        frame_numbers = set()
        left_packets = 0
        right_packets = 0

        with open(output_file, 'wb') as f:
            while time.time() - start_time < duration_seconds:
                current_time = time.time()

                # Re-send 0x95 every 0.3 seconds to maintain streaming
                if current_time - last_trigger_time > 0.3:
                    try:
                        self.device.write(0x04, cmd_95, timeout=100)
                        # Quick read of ACK from EP 0x85
                        try:
                            self.device.read(0x85, 64, timeout=50)
                        except:
                            pass
                        last_trigger_time = current_time
                        if read_count > 0 and read_count % 50 == 0:
                            print(f"  [Re-triggered 0x95 at {read_count} packets]")
                    except:
                        pass

                try:
                    data = self.device.read(0x82, 16384, timeout=50)
                    if data:
                        read_count += 1
                        data_bytes = bytes(data)

                        # Classify packet by header
                        if len(data_bytes) >= 2:
                            header = data_bytes[:2]
                            if header == b'\xaa\x8f':
                                aa8f_packets += 1
                                packet_type = "FRAME"

                                # Decode frame info
                                if len(data_bytes) >= 12:
                                    packet_id = int.from_bytes(data_bytes[2:4], 'little')
                                    frame_num = int.from_bytes(data_bytes[4:6], 'little')
                                    camera_flag = data_bytes[8]

                                    frame_numbers.add(frame_num)
                                    if camera_flag == 0xfc:
                                        left_packets += 1
                                    elif camera_flag == 0xfd:
                                        right_packets += 1

                            elif header == b'\xa2\xc4':
                                a2c4_packets += 1
                                packet_type = "IMU"
                            else:
                                other_packets += 1
                                packet_type = "OTHER"
                        else:
                            other_packets += 1
                            packet_type = "SHORT"

                        # Log first 20 and then every 100th packet
                        if read_count <= 20 or read_count % 100 == 0:
                            print(f"  Read {read_count}: {len(data_bytes):5d} bytes [{packet_type:5s}] header: {data_bytes[:16].hex()}")

                        # Track packet info
                        packet_info.append((len(all_data), len(data_bytes), data_bytes[:8].hex()))

                        # Write to file and accumulate
                        f.write(data_bytes)
                        all_data.extend(data_bytes)

                        timeout_count = 0  # Reset timeout counter on success

                except usb.core.USBError as e:
                    if "timed out" in str(e).lower() or e.errno == 110:
                        timeout_count += 1
                    else:
                        print(f"  Error: {e}")

        elapsed = time.time() - start_time
        print(f"\n=== Capture Summary ===")
        print(f"Duration: {elapsed:.2f} seconds")
        print(f"Total reads: {read_count}")
        print(f"Total bytes: {len(all_data)}")
        print(f"Data rate: {len(all_data) / elapsed / 1024:.1f} KB/s")
        print(f"\nPacket types:")
        print(f"  aa8f (FRAME): {aa8f_packets}")
        print(f"  a2c4 (IMU):   {a2c4_packets}")
        print(f"  Other:        {other_packets}")
        print(f"\nStereo camera analysis:")
        print(f"  Left camera (0xfc):  {left_packets} packets")
        print(f"  Right camera (0xfd): {right_packets} packets")
        print(f"  Unique frame numbers: {len(frame_numbers)}")
        if frame_numbers:
            print(f"  Frame number range: {min(frame_numbers)} - {max(frame_numbers)}")

        # Analyze aa8f packets
        if aa8f_packets > 0:
            print(f"\n=== Analyzing aa8f (FRAME) packets ===")
            frame_packets = [(off, sz, hdr) for off, sz, hdr in packet_info if hdr.startswith('aa8f')]

            if frame_packets:
                sizes = [sz for _, sz, _ in frame_packets]
                print(f"Frame packet sizes: min={min(sizes)}, max={max(sizes)}, avg={sum(sizes)/len(sizes):.0f}")

                # Show first few frame headers
                print(f"\nFirst 5 frame packet headers:")
                for i, (off, sz, hdr) in enumerate(frame_packets[:5]):
                    # Read more of the header from captured data
                    if off + 32 <= len(all_data):
                        full_header = all_data[off:off+32]
                        print(f"  Frame {i+1}: offset={off:6d}, size={sz:5d}")
                        print(f"           {full_header.hex()}")
                        # Try to decode some fields
                        if len(full_header) >= 16:
                            # Guess at field meanings
                            field1 = int.from_bytes(full_header[2:4], 'little')
                            field2 = int.from_bytes(full_header[4:6], 'little')
                            field3 = int.from_bytes(full_header[6:8], 'little')
                            print(f"           Fields: [{field1:5d}] [{field2:5d}] [{field3:5d}]")

                # Check if sizes are consistent (could be fixed frame size)
                unique_sizes = set(sizes)
                if len(unique_sizes) <= 3:
                    print(f"\nFrame sizes are consistent: {unique_sizes}")
                    total_frame_size = max(sizes)
                    # Estimate resolution
                    if total_frame_size == 16384:
                        print("  16384 bytes could be:")
                        print("    - 128x128 grayscale (exactly)")
                        print("    - 128x64 x2 cameras (stereo)")
                        print("    - Compressed frame chunk")

                # Calculate expected stereo camera frame size
                print("\n  Reference: For 640x480 stereo B&W cameras:")
                print("    - Single camera: 640*480 = 307,200 bytes")
                print("    - Stereo pair:   307,200 * 2 = 614,400 bytes")
                print("    - At 30fps:      18.4 MB/s")

        print(f"\n** Raw data saved to: {output_file} **")
        print(f"Analyze with:")
        print(f"  hexdump -C {output_file} | head -200")
        print(f"  file {output_file}")

        # If we got substantial data, try to detect image patterns
        if len(all_data) > 10000:
            print("\n=== Pattern Analysis ===")

            # Look for repeating patterns that might indicate frame boundaries
            aa8f_offsets = []
            for i in range(len(all_data) - 2):
                if all_data[i:i+2] == b'\xaa\x8f':
                    aa8f_offsets.append(i)

            if len(aa8f_offsets) >= 2:
                gaps = [aa8f_offsets[i+1] - aa8f_offsets[i] for i in range(len(aa8f_offsets)-1)]
                print(f"Found {len(aa8f_offsets)} 'aa8f' markers")
                print(f"Gaps between markers: {gaps[:10]}...")

                # Most common gap size
                if gaps:
                    from collections import Counter
                    gap_counts = Counter(gaps)
                    most_common = gap_counts.most_common(3)
                    print(f"Most common gap sizes: {most_common}")

    def try_control_transfers(self):
        """Try USB control transfers - vendor-specific setup might be needed."""
        print("\n=== Trying USB Control Transfers ===")
        print("Some cameras need vendor-specific control requests to initialize")

        # Common vendor request types
        requests_to_try = [
            # (request_type, bRequest, wValue, wIndex, description)
            (0xC0, 0x01, 0x0000, 0x0000, "vendor read 0x01"),
            (0xC0, 0x02, 0x0000, 0x0000, "vendor read 0x02"),
            (0xC0, 0x81, 0x0000, 0x0000, "vendor read 0x81"),
            (0xC0, 0x86, 0x0000, 0x0000, "vendor read 0x86 (common UVC)"),
            (0x40, 0x01, 0x0001, 0x0000, "vendor write enable 0x01"),
            (0x40, 0x01, 0x0000, 0x0000, "vendor write disable 0x01"),
            (0x40, 0x02, 0x0001, 0x0000, "vendor write enable 0x02"),
            (0xC0, 0x00, 0x0000, 0x0000, "vendor read status"),
            (0xC0, 0x10, 0x0000, 0x0000, "vendor read 0x10"),
            (0x40, 0x10, 0x0001, 0x0000, "vendor write 0x10 enable"),
        ]

        working_requests = []
        for req_type, req, value, index, desc in requests_to_try:
            try:
                if req_type & 0x80:  # IN transfer (read)
                    result = self.device.ctrl_transfer(req_type, req, value, index, 64, timeout=500)
                    if result and len(result) > 0:
                        print(f"  {desc}: {len(result)} bytes - {bytes(result).hex()[:40]}")
                        working_requests.append((req_type, req, value, index))
                else:  # OUT transfer (write)
                    result = self.device.ctrl_transfer(req_type, req, value, index, [], timeout=500)
                    print(f"  {desc}: OK (wrote 0 bytes)")
                    working_requests.append((req_type, req, value, index))
            except usb.core.USBError as e:
                if "pipe" in str(e).lower() or "stall" in str(e).lower():
                    pass  # Expected for unsupported requests
                else:
                    print(f"  {desc}: {e}")

        if working_requests:
            print(f"\nWorking control requests: {len(working_requests)}")

            # Try sending a working control request then 0xEA
            for req_type, req, value, index in working_requests:
                try:
                    # Send the control request
                    if req_type & 0x80:
                        self.device.ctrl_transfer(req_type, req, value, index, 64, timeout=500)
                    else:
                        self.device.ctrl_transfer(req_type, req, value, index, [], timeout=500)

                    # Now try 0xEA
                    cmd_ea = bytes([0xFA, 0x55, 0xEA, 0x00, 0x08, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
                    self.device.write(0x04, cmd_ea, timeout=500)
                    resp_ea = self.device.read(0x85, 512, timeout=500)
                    if resp_ea and len(resp_ea) >= 4:
                        ea_status = resp_ea[3]
                        if ea_status == 0x00:
                            print(f"\n** CTRL({req_type:02x}, {req:02x}, {value:04x}, {index:04x}) -> 0xEA SUCCESS! **")
                except:
                    pass

    def extended_stream_probe(self, duration_seconds=5):
        """Send various start commands and do extended reads to catch streaming."""
        print("\n=== Extended Stream Probe ===")
        print(f"Will attempt to start streaming and read for {duration_seconds} seconds")

        # Commands that might start streaming
        start_commands = [
            # FA55 protocol variations
            (0x04, self.build_stereo_command(0xEA, 0x0800, 0x01, 0x00)),  # Standard
            (0x04, self.build_stereo_command(0xEA, 0x0800, 0x01, 0x01)),  # Camera 1
            (0x04, self.build_stereo_command(0x01, 0x0800, 0x01, 0x00)),  # cmd=0x01
            (0x04, self.build_stereo_command(0x02, 0x0800, 0x01, 0x00)),  # cmd=0x02
            (0x04, self.build_stereo_command(0x10, 0x0800, 0x01, 0x00)),  # cmd=0x10
            # FF FD protocol - try some that might enable streaming
            (0x06, bytes([0xFF, 0xFD, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])),
            (0x06, bytes([0xFF, 0xFD, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])),
        ]

        # Send all start commands
        print("\nSending potential start commands...")
        for ep, cmd in start_commands:
            try:
                self.device.write(ep, cmd, timeout=500)
                print(f"  EP 0x{ep:02x} <- {cmd.hex()}")
                # Quick read to clear ACK
                try:
                    resp_ep = 0x85 if ep == 0x04 else 0x87
                    self.device.read(resp_ep, 512, timeout=50)
                except:
                    pass
            except usb.core.USBError as e:
                print(f"  Error: {e}")
            time.sleep(0.02)

        # Now do extended reads from all IN endpoints
        print(f"\nReading from all IN endpoints for {duration_seconds} seconds...")
        endpoints_to_read = [0x82, 0x85, 0x87]
        start_time = time.time()
        total_bytes = {ep: 0 for ep in endpoints_to_read}
        large_packets = {ep: [] for ep in endpoints_to_read}
        read_count = 0

        while time.time() - start_time < duration_seconds:
            for ep in endpoints_to_read:
                try:
                    # Try large buffer in case image data
                    data = self.device.read(ep, 16384, timeout=20)
                    if data:
                        total_bytes[ep] += len(data)
                        read_count += 1
                        # Track large or unusual packets
                        if len(data) > 512 or (len(data) > 20 and sum(1 for b in data[20:] if b != 0) > 10):
                            large_packets[ep].append((len(data), bytes(data[:64])))
                            print(f"  ** EP 0x{ep:02x}: {len(data)} bytes (non-trivial data!)")
                except usb.core.USBError:
                    pass

        print(f"\n=== Results after {duration_seconds} seconds ({read_count} reads) ===")
        for ep in endpoints_to_read:
            print(f"EP 0x{ep:02x}: {total_bytes[ep]} total bytes")
            if large_packets[ep]:
                print(f"  Large/interesting packets: {len(large_packets[ep])}")
                for size, sample in large_packets[ep][:3]:
                    print(f"    {size} bytes: {sample.hex()}")


def main():
    parser = argparse.ArgumentParser(description="VITURE Stereo Camera Probe Tool")
    parser.add_argument('--list-endpoints', '-l', action='store_true',
                        help="List all USB endpoints")
    parser.add_argument('--probe', '-p', action='store_true',
                        help="Probe stereo cameras with discovered command")
    parser.add_argument('--scan', '-s', action='store_true',
                        help="Scan with various command variations")
    parser.add_argument('--stream', action='store_true',
                        help="Try to start streaming and read continuously")
    parser.add_argument('--start-cmds', action='store_true',
                        help="Try various potential start commands")
    parser.add_argument('--ep87', action='store_true',
                        help="Explore EP 0x87 (potential camera data)")
    parser.add_argument('--ep06', action='store_true',
                        help="Probe EP 0x06 with FF FD commands to find camera start")
    parser.add_argument('--extended', '-e', type=int, nargs='?', const=5,
                        help="Extended stream probe (optional: seconds, default 5)")
    parser.add_argument('--exact', action='store_true',
                        help="Execute exact disassembly sequence (0x951ecc)")
    parser.add_argument('--ctrl', action='store_true',
                        help="Try USB control transfers for initialization")
    parser.add_argument('--discover', '-d', action='store_true',
                        help="Try discovered initialization (0x95 cmd, 0x90-0x9F scan)")
    parser.add_argument('--uvc', action='store_true',
                        help="Explore UVC interfaces, alternate settings, and EP 0x82")
    parser.add_argument('--capture', '-c', type=int, nargs='?', const=5,
                        help="Capture EP 0x82 stream after 0x95 (optional: seconds, default 5)")
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
        elif args.stream:
            probe.try_streaming()
        elif args.start_cmds:
            probe.try_start_commands()
        elif args.ep87:
            probe.explore_ep87()
        elif args.ep06:
            probe.probe_ep06_commands()
        elif args.extended is not None:
            probe.extended_stream_probe(args.extended)
        elif args.exact:
            probe.exact_disasm_sequence()
        elif args.ctrl:
            probe.try_control_transfers()
        elif args.discover:
            probe.try_discovered_init()
        elif args.uvc:
            probe.explore_uvc_and_alternate()
        elif args.capture is not None:
            probe.capture_ep82_stream(args.capture)
        else:
            # Default: basic probe
            probe.probe_stereo_cameras()
    finally:
        probe.release()

    return 0


if __name__ == "__main__":
    sys.exit(main())
