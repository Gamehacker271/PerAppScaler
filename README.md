# PerAppScaler

[English](README.md) | [Español](README_ES.md)

**PerAppScaler** is an open-source Android application that allows you to independently adjust resolution (downscaling) and frame rate limits (FPS) on a per-app or per-game basis, without altering the global screen resolution (`wm size`).

## Key Features

- **No Root Required at Runtime:** Operates natively as a privileged system application (`priv-app`) using the `android.permission.MANAGE_GAME_MODE` permission.
- **Broad Compatibility:** Supports Android 12 (API 31) through Android 17+ (API 37).
- **Zero-Latency Hardware-Accelerated Scaling:** Utilizes `GameManagerService` interventions managed directly by SurfaceFlinger and the hardware display processor (DPU) of Qualcomm/MediaTek chipsets. SystemUI and background apps remain at native 1080p, while the target game renders at the selected scale factor (e.g., 70% = 756×1680 px), drastically reducing GPU load, thermal throttling, and battery consumption.
- **Multilingual Support (i18n):** Built-in in-app language switcher supporting:
  - English (`en`)
  - Spanish (`es`)
  - Portuguese (`pt`)
  - Indonesian (`in`)
  - Vietnamese (`vi`)
  - Russian (`ru`)
  - Simplified Chinese (`zh-CN`)
  - Traditional Chinese (`zh-TW`)
  - Arabic (`ar` with native RTL layout support)
- **Quick Presets & Manual Control:**
  - Quality (100% native)
  - Balanced (80%)
  - Performance (70%)
  - Extreme Battery Saver (50%)
  - Continuous resolution slider (30% to 100% in 5% increments)
  - FPS limiter (30, 45, 60, 90, 120 FPS or Unlimited)
- **Real-Time Calculation:** Instant calculation and preview of the resulting resolution and the percentage of saved pixels.
- **Quick Filters:** Filter by All Apps, Games, and Modified Apps.

---

## Privileged Permission Architecture

The application declares the `signature|privileged` protection level permissions:
```xml
<permission name="android.permission.MANAGE_GAME_MODE"/>
<permission name="android.permission.INTERACT_ACROSS_USERS"/>
```

By placing the whitelist file at `/system/etc/permissions/privapp-permissions-com.gamehac.perappscaler.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.gamehac.perappscaler">
        <permission name="android.permission.MANAGE_GAME_MODE"/>
        <permission name="android.permission.INTERACT_ACROSS_USERS"/>
    </privapp-permissions>
</permissions>
```
Android's `PackageManager` automatically grants these permissions during system boot, without requiring a platform signature or superuser (root) permissions for the app.

---

## AOSP ROM Integration

To build PerAppScaler directly as part of an AOSP ROM source tree (LineageOS, crDroid, EvolutionX, PixelOS, etc.):

1. Clone this repository into your ROM source tree (e.g., `packages/apps/PerAppScaler`).
2. In your device or product makefile (e.g., `device.mk` or `common.mk`), add:
```makefile
$(call inherit-product, packages/apps/PerAppScaler/perappscaler.mk)
```
3. Build normally (`m PerAppScaler` or full ROM build).

---

## Testing via Root Module (Magisk / KernelSU / APatch)

To test the application on already rooted devices without recompiling the entire ROM:
1. The repository includes a pre-configured module structure in `magisk_module/`.
2. Place the compiled `PerAppScaler.apk` into `magisk_module/system/priv-app/PerAppScaler/PerAppScaler.apk`.
3. Compress the contents of `magisk_module/` into a `.zip` file and flash it via Magisk, KernelSU, or APatch.

---

## License

This project is open-source under the Apache License 2.0.
Developed by **Gamehac_RA** & **Google DeepMind Antigravity**.
