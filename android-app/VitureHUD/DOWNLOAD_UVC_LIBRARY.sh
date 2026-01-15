#!/bin/bash
# Download UVCCamera library directly from GitHub

echo "Downloading UVCCamera library..."

# Create libs directory
mkdir -p app/libs

# Download the AAR file from a known good source
# This is a mirror of the UVCCamera library
curl -L -o app/libs/usbCameraCommon.aar \
  "https://github.com/saki4510t/UVCCamera/raw/master/usbCameraCommon/build/outputs/aar/usbCameraCommon-release.aar"

curl -L -o app/libs/libuvccamera.aar \
  "https://github.com/saki4510t/UVCCamera/raw/master/libuvccamera/build/outputs/aar/libuvccamera-release.aar"

echo "Done! Now update build.gradle.kts to use local AAR files."
