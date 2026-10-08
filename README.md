# Visible Zmanim for Android

Sunrise and sunset over the real hills and mountains around you, not a flat sea-level horizon, plus the zmanim built on them. The app is a small native client (under 100 KB) for the [visible-zmanim](https://github.com/flipphoneguy/visible-zmanim) API, where the calculation, the terrain data and how it all works are documented.

**Download:** [VisibleZmanim.apk](https://github.com/flipphoneguy/visible-zmanim-android/releases/latest/download/VisibleZmanim.apk) (Android 6 and up). The app can update itself from the About screen.

It covers address search, your GPS location, saved places, a day or a whole month, visible, elevation and standard sea-level times side by side, the horizon drawing, and metric or imperial units. Also available as a website: [tools.flipphoneguy.duckdns.org/zmanim](https://tools.flipphoneguy.duckdns.org/zmanim).

## Build

No Gradle. `./build.sh` builds the APK either on a desktop with the Android SDK (`~/Android/Sdk` and a JDK) or on the phone in Termux (run `./setup.sh` once). App name, package and version come from `project.json` and `VERSION`.

## License

GPL-3.0
