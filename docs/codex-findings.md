# Codex Findings

## Issues Observed

1) Android camera status is inconsistent across docs and code.
- `android-app/VitureHUD/README.md` and `docs/PROJECT_SPEC.md` describe camera preview and capture as working MVP features.
- `android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/MainActivity.kt` has camera mode disabled and capture button only shows a toast.

2) DeX camera feasibility conflicts with the Android README and project spec.
- `docs/DEX_CAMERA_TEST_RESULTS.md` concludes USB cameras are not accessible in DeX mode on Android.
- `android-app/VitureHUD/README.md` and `docs/PROJECT_SPEC.md` present camera access in DeX as current behavior.

3) Manifest permissions do not match the camera plan.
- `docs/CAMERA_INTEGRATION_PLAN.md` states CAMERA permission is already configured.
- `android-app/VitureHUD/app/src/main/AndroidManifest.xml` does not declare `android.permission.CAMERA` (only USB host and storage).

4) HUD sizing guidance in the Android README does not match the current layout.
- `android-app/VitureHUD/README.md` references `app:layout_constraintWidth_percent` for adjustable HUD width.
- `android-app/VitureHUD/app/src/main/res/layout/activity_main.xml` uses fixed `650dp` width and fixed margins instead of percent constraints.

## CAMERA_INTEGRATION_PLAN Notes

- The plan assumes Android can access the Viture camera via UVC on the phone, but the DeX test results indicate the OS does not expose USB cameras at all. If those results are definitive, the plan should call out that DeX mode is a blocker unless a kernel/UVC driver path is added or a different hardware approach is taken.
- The plan is clear on the local-module UVCCamera approach, but it should explicitly reconcile required permissions and manifest changes (USB host, CAMERA, storage) to avoid build-time or runtime surprises.
- The plan references local cloning and `serenegiant` repository use; the repo currently ships a `DOWNLOAD_UVC_LIBRARY.sh` script that pulls AARs directly. It may be useful to state which path is the preferred default and keep only one path in the mainline plan to reduce confusion.
