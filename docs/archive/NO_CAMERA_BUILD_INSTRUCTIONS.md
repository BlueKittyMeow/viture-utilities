# Build Without Camera (Temporary Workaround)

If you want to test the app structure and text editor WITHOUT camera functionality, follow these steps:

## Step 1: Comment Out Camera Dependency

In `app/build.gradle.kts`, comment out the UVCCamera line:

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // TEMPORARILY COMMENTED OUT
    // implementation("com.github.saki4510t:UVCCamera:master-SNAPSHOT")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

## Step 2: Comment Out Camera Code

In `app/src/main/kotlin/com/viture/hud/MainActivity.kt`, comment out camera-related imports and code:

At the top, comment out:
```kotlin
// import com.serenegiant.usb.*
// import com.serenegiant.widget.UVCCameraTextureView
```

In onCreate(), comment out:
```kotlin
// cameraPreview = findViewById(R.id.cameraPreview)
// initializeUSBMonitor()
```

Comment out capture button action:
```kotlin
captureButton.setOnClickListener {
    // capturePhoto()
    Toast.makeText(this, "Camera not enabled in this build", Toast.LENGTH_SHORT).show()
}
```

Comment out all camera-related functions:
- `initializeUSBMonitor()`
- `openCamera()`
- `closeCamera()`
- `capturePhoto()`
- `getUSBMonitor()`
- `onDialogResult()`

## Step 3: Update Layout

In `app/src/main/res/layout/activity_main.xml`, remove the camera view:

Delete or comment out the `UVCCameraTextureView` section (lines ~37-44).

## Step 4: Build

Now you can build and run:
```bash
./gradlew assembleDebug
```

## What Will Work

✓ Text editor (fully functional)
✓ DeX display targeting
✓ Black transparent background
✓ UI layout and theme
✓ Settings button

✗ Camera preview (disabled)
✗ Photo capture (shows toast instead)

## When Ready

Once you figure out the UVCCamera dependency, just uncomment everything and rebuild!
