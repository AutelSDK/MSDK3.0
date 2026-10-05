# MSDK 3.0

Autel Robotics Mobile SDK (MSDK) 3.0 sample application. This project demonstrates how to integrate the Autel MSDK 3.0 into an Android app, including device connection, flight control, camera, gimbal, album, waypoint mission and other capabilities, along with a set of debug tools.

## Requirements

- Android Studio (AGP 7.2+)
- minSdk 23, targetSdk 32, Java 11
- An ARM64 Android device

## Getting the SDK

The MSDK binary is provided as an AAR in [`debugtools/libs/`](debugtools/libs/):

```text
debugtools/libs/autel-msdk2-release_V3.0.00.35.aar
```

It is referenced by the `debugtools` module in [debugtools/build.gradle](debugtools/build.gradle):

```groovy
api(name: 'autel-msdk2-release_V3.0.00.35', ext: 'aar')
```

The repository layout is declared in [settings.gradle](settings.gradle) via a flatDir repository pointing at `debugtools/libs`. When you upgrade to a newer AAR, drop the new file into that directory and update the name above.

## Project Structure

- **`app/`** — Sample application shell (`com.autel.drone.demo`).
- **`debugtools/`** — Sample/debug tools module built on the MSDK: device manager, flight control, virtual stick, camera, gimbal, waypoint mission, AI detection/tracking, fly zone and other demo fragments.
- **`dependencies.gradle`** — Centralized dependency and version declarations.

## Build

1. Clone this repository
2. Open with Android Studio
3. Build and run on an ARM64 Android device
4. Signing is optional: by default debug builds use the standard Android debug key and release builds are unsigned. To sign, provide your own keystore and set `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` in `local.properties` or `gradle.properties`, placing the keystore next to [app/build.gradle](app/build.gradle). Never commit a production keystore.

## Documentation

Full API documentation and guides: [Autel Developer Portal](https://developer.autelrobotics.com)

## License

The sample code in this repository is licensed under the **MIT License**. See [LICENSE.txt](LICENSE.txt) for details.

The Autel Mobile SDK (MSDK) binary files (`.aar`) are subject to the **Autel Robotics End User License Agreement**. Please refer to the [Autel Developer Portal](https://developer.autelrobotics.com) for the full terms.
