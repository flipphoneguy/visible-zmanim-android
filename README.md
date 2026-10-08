# Android App Template

Minimal Android app template that builds entirely on-device in Termux. No Gradle, no Android Studio — just `aapt2`, `ecj`, `d8`, and `apksigner`.

Includes a Material Light UI with d-pad/keyboard focus support, a self-update mechanism (GitHub Releases), and an info screen with profile/repo links.

## Termux setup

```sh
chmod +x setup.sh
./setup.sh
```

This installs all required packages (`aapt2`, `ecj`, `d8`, `apksigner`, `zip`), downloads `android.jar` (API 34 stubs) and `framework-res.apk` (system resource definitions) to `~/.android/`, and generates a `debug.keystore` for signing. These SDK files are API-level specific, not device-specific — they work on any Termux installation.

<details>
<summary>Manual setup (without setup.sh)</summary>

Install packages:

```sh
pkg install aapt2 ecj d8 apksigner zip
```

You need these files in `~/.android/`:

| File | What it is | Where to get it |
|---|---|---|
| `android.jar` | Android API stubs | Extract from an Android SDK (`platforms/android-XX/android.jar`) or copy from a device at `/system/framework/framework.jar` |
| `framework-res.apk` | System resource definitions | Copy from a device: `adb pull /system/framework/framework-res.apk` |
| `debug.keystore` | Signing key | Generate: `keytool -genkey -v -keystore ~/.android/debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Debug"` |

</details>

## Build

```sh
chmod +x build.sh
./build.sh
```

Output APK name is derived from the `name` field in `project.json` (spaces stripped). For example, `"name": "My App"` produces `MyApp.apk`.

## Project structure

```
├── project.json            # App identity and constants
├── VERSION                 # Semver string (e.g. 1.0.0)
├── build.sh                # Build pipeline
├── AndroidManifest.xml     # App manifest (synced by build.sh)
├── res/
│   ├── drawable/           # Button/card/focus state drawables
│   ├── layout/             # activity_main.xml, activity_info.xml
│   ├── mipmap-anydpi-v26/  # Adaptive icon
│   ├── values/             # colors.xml, themes.xml, strings.xml
│   └── values-night/       # Dark mode color/theme overrides
├── src/                    # Java source (package path)
├── libs/                   # Optional jars (auto-included in classpath)
├── tmp/                    # Scratch space (gitignored)
└── build/                  # Build artifacts (gitignored)
```

## Configuration

**project.json** holds app identity — edit this first:

```json
{
    "package": "com.flipphoneguy.app",
    "name": "App Template",
    "min_sdk": 23,
    "target_sdk": 35,
    "repo": "flipphoneguy/app-template",
    "github_profile": "https://github.com/flipphoneguy"
}
```

**VERSION** is a single-line semver (`1.0.0`). Bump this for releases. `build.sh` reads it to set `versionCode` and `versionName` in the manifest and in `BuildConfig.java`.

Version code formula: `major * 10000 + minor * 100 + patch`.

## Starting a new project from this template

1. Copy the directory
2. Edit `project.json` (package, name, repo, etc.)
3. Rename `src/com/flipphoneguy/app/` to match your package path
4. Find-replace `package com.flipphoneguy.app` in all `.java` files
5. Set `VERSION` to `1.0.0`
6. Run `./build.sh`

`build.sh` syncs `AndroidManifest.xml` with your `project.json` values automatically on each build (package, version, provider authority).

## What's included

### UI
- Material Light theme with automatic dark mode support (follows system on Android 10+)
- Manual theme toggle in the info screen (System Default / Light / Dark), persisted across restarts
- Indigo color palette (light), dark neutral palette with emerald accents (dark)
- Card-based layout with rounded corners
- D-pad/keyboard focus support: declarative `nextFocusUp/Down` chains on all interactive elements, visible focus states (white border on buttons, indigo highlight on cards)

### Self-update (Info screen)
- Checks GitHub Releases API for a newer version
- Downloads APK, then prompts user to choose install method:
  - **Root**: copies to `/data/local/tmp`, runs `pm install -r`
  - **System installer**: launches the system package installer via intent

### Build system
- `build.sh` reads `project.json` + `VERSION`, generates `BuildConfig.java` with compile-time constants (`VERSION_NAME`, `REPO`, `GITHUB_PROFILE`, etc.)
- 5-step pipeline: compile resources, link, compile Java, dex, package+sign
- Jars in `libs/` are automatically added to the classpath and dexed

## Termux build-tool quirks

Things that will bite you when writing code against this template — none of these are obvious from error messages.

- **`ecj` wrapper hardcodes `-7`** (Java 7 compliance). Adding `-source`/`-target`/`-1.8` to your build invocation produces `duplicate compliance setting`. If you need a different language level, edit the wrapper script or invoke `ecj.jar` directly.
- **`d8` only accepts class major version ≤ 55** (Java 11). A library jar compiled with Java 17 (major 61) or 21 (major 65) will fail dexing with `Unsupported class file major version`. Workaround: extract the jar and rewrite byte 7 of every `.class` file to `0x34` (Java 8 / major 52), then repack. As long as the library uses no post-Java-8 APIs at runtime, this works.

## License

See [LICENSE](LICENSE).
