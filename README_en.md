# Always Allow App-to-App Jumps · AppStartConfirm

> Restores the native **"Always allow to open"** checkbox on the app-jump confirmation dialog in the ColorOS permission manager.
> A standalone LSPosed module extracted from [coloros-mod](https://github.com/rikumi/coloros-mod) (MIT).

![banner](docs/banner.png)

**Mechanism-level compatibility** · no version whitelist · **10.3KB** · verified on **ColorOS 17 / Android 17**
(logic baseline: SecurityPermission 16.08.003, decompiled and verified)

---

## Features

- **Restores the native "Always allow to open" checkbox**: the new dialog (`activity_start_confirm_version=2`) only offers "Allow for 30 days". This module rewrites the Intent version back to 0 before `AppStartConfirmDialogActivity#onCreate`, making the system use the legacy dialog with the "Always allow to open" checkbox
- **Single hook point**: no click-callback patching, no direct grant API calls; payment protection, mini-program interception, blacklist and game-pay interception keep their native paths
- **Works out of the box**: no UI, no settings, no launcher icon, static-scope preloading — nothing to configure once enabled
- **10.3KB**: 7KB dex + xposed assets + two strings; zero dead weight (no service library, no Kotlin/Compose/androidx)
- **Diagnostics**: every dialog trigger logs `action/version/start_type/hit` to the LSPosed log for one-shot root-causing

## Install & Use

1. Install **LSPosed 2.x** (libxposed API 102) or another compatible framework
2. Install the APK and enable the module in LSPosed
3. **Check scope "Permission manager" (`com.oplus.securitypermission`)** and reboot
4. Trigger a jump between a not-yet-authorized pair of apps — the dialog should show the "Always allow to open" checkbox; checking it saves a long-term grant

> The scope list may display the app as "Permission manager"; the package name is `com.oplus.securitypermission`.

## Compatibility

- Mechanism is **independent of system version** (no version whitelist in code): works as long as the system keeps the `AppStartConfirmDialogActivity` class name and the `version 2 → legacy dialog` branch
- Verified: **ColorOS 17 / Android 17** (2026-10)
- Logic baseline: SecurityPermission `16.08.003` (SHA256 `a07868b7dbbfb3af9bea64f3488f8a3fe847ac0aec639f0ac0f51c01b0bd9c22`)
- If a future system version changes the class name or branch, the hook fails with only a `HOOK FAIL` log — the system is unaffected
- Rooting/modding carries risk; back up first

## Build

Requires JDK 17+, Android SDK (platform 37), access to Google Maven / Maven Central.

Pinned: AGP `8.9.2` + Gradle `8.13` + `compileSdk 37`.

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

Release enables R8 shrinking (`minifyEnabled true`, keep rules configured, 10.3KB). Default debug signing is installable out of the box; for a stable signature see `signing.properties.example`.

## Verification

```bash
python3 scripts/check-app-start-confirm.py
```

726 JVM checks: version boundaries, risk-type boundaries, Intent copies, hook target registration, scope wiring, string resources.

## How it works

The new ColorOS jump-confirmation dialog (`activity_start_confirm_version=2`) dropped the "Always allow to open" checkbox. This module, before `com.oplusos.securitypermission.permission.ui.AppStartConfirmDialogActivity#onCreate`:

1. When the action is `oplus.app.action.CHECK_ALLOW_START_ACTIVITY`, the version is 2 and `start_type ∈ {0, 5, 6}`, rewrites a copy of the Intent to version 0;
2. Native `version != 2` uses the legacy dialog (with the "Always allow to open" checkbox);
3. Checking it and allowing saves a long-term grant without expiry (native `ALWAYS_ALLOW_START=101`).

`start_type=7` (blacklist interception) and payment/mini-program scenarios keep their native paths untouched.

## Layout

```
app/src/main/
├── AndroidManifest.xml            # label/description only (no Activity/Provider/icon)
├── java/io.github.kepsilent.appstartconfirm/
│   ├── XposedInit.java            # libxposed entry (onModuleLoaded/Loaded/Ready)
│   ├── hooks/AppStartConfirmHooks.java  # the only hook + diagnostics
│   └── xposed/                    # legacy Xposed API compat layer (slimmed)
└── resources/META-INF/xposed/     # java_init.list / module.prop / scope.list
scripts/check-app-start-confirm.py
docs/banner.png
```

## Size history

| Version | Size | Notes |
| --- | --- | --- |
| v1.x | ~365 KB | initial extraction with full UI & settings |
| v3.0.4 | 37.8 KB | no icon + one toggle + R8 |
| v3.0.8 | 27.9 KB | hand-trimmed compat layer (830 → 130 lines) |
| **v3.0.9** | **10.3 KB** | **service-library dead weight removed + R8 on business code** |

## License

- Hook logic & implementation derived from [rikumi/coloros-mod](https://github.com/rikumi/coloros-mod), **MIT License**
- Banner visual follows the [EGD (Elegant Geek Design)](https://github.com/Kepsilent/Elegant-Geek-Design) design language
