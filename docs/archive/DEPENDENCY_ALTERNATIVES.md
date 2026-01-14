# UVCCamera Dependency Alternatives

If `com.github.saki4510t:UVCCamera:2.3.2` doesn't work, try these alternatives:

## Option 1: Different Version Tags

Try these versions in `app/build.gradle.kts`:

```kotlin
// Version without 'v' prefix
implementation("com.github.saki4510t:UVCCamera:3.1.0")

// Or with 'v' prefix
implementation("com.github.saki4510t:UVCCamera:v3.1.0")

// Older stable version
implementation("com.github.saki4510t:UVCCamera:2.4.0")

// Commit hash (always works)
implementation("com.github.saki4510t:UVCCamera:master-SNAPSHOT")
```

## Option 2: Use AAR File Directly

If JitPack isn't working, download the AAR directly:

1. **Download from GitHub releases:**
   - Go to: https://github.com/saki4510t/UVCCamera/releases
   - Download the `.aar` file

2. **Add to project:**
   ```bash
   mkdir -p app/libs
   # Place the downloaded .aar file in app/libs/
   ```

3. **Update build.gradle.kts:**
   ```kotlin
   dependencies {
       // Replace the github dependency with:
       implementation(files("libs/UVCCamera.aar"))

       // Keep other dependencies...
   }
   ```

## Option 3: Clone and Build Locally

If you want the latest version:

```bash
cd ~
git clone https://github.com/saki4510t/UVCCamera.git
cd UVCCamera
./gradlew assembleRelease
# Copy the generated AAR to your project
```

## Option 4: Use Alternative USB Camera Library

If UVCCamera keeps failing, try this alternative:

```kotlin
// In build.gradle.kts, replace UVCCamera with:
implementation("com.jiangdg.android.usbcamera:libusbcamera:3.1.0")
```

This library has similar functionality and is more actively maintained.

## Current Working Configuration

As of now, we're using:
```kotlin
implementation("com.github.saki4510t:UVCCamera:2.3.2")
```

This is a stable version that should work with JitPack.

## Troubleshooting

If sync keeps failing:

1. **Check internet connection** - JitPack needs to download
2. **Clear Gradle cache:**
   ```bash
   ./gradlew clean
   rm -rf ~/.gradle/caches
   ```
3. **Invalidate Android Studio caches:**
   - File → Invalidate Caches → Invalidate and Restart

4. **Check JitPack status:**
   - Visit: https://jitpack.io/#saki4510t/UVCCamera
   - See if the version exists and builds successfully
