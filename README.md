# Stash Reader

A personal Android reader for learning, in the spirit of Deepstash: one vertical feed of things worth reading. You choose the sources and topics, the app ranks the items, you read, keep and export what you learned.

**Download the APK (always the latest release):**
https://github.com/jacekkotowski/apk-news-reader/releases/latest/download/StashReader.apk

Package `pl.stashreader`. The interface is in English; sources in any language are fine. The plan and the step list are in [PLAN.md](PLAN.md); each release has its own notes.

## Design in short

- No Gradle, no third-party libraries. `build.sh` calls aapt2, javac, d8, zipalign and apksigner.
- The screen is a WebView showing `app/assets/index.html`. A small Java bridge does everything that touches the network, the database or Android.
- Classes that must stay free of Android imports are listed in `tests/core.txt` and are tested on the PC with `tests/run.sh`.
- One vendored JavaScript file is planned for step 2: Mozilla Readability (Apache-2.0), see [THIRD_PARTY.md](THIRD_PARTY.md).

## Build

You need a JDK 17 and an Android SDK with `platforms;android-36` and `build-tools;36.0.0`.

```bash
bash tests/run.sh                       # tests on the PC
export ANDROID_SDK_ROOT=/path/to/sdk
bash build.sh                           # needs signing.env, see below
```

`signing.env` holds `KS_FILE` (the keystore file) and `KS_PASS`. Both files are never committed. The real key lives in the private repo `apk-signing-keys`, folder `apk-news-reader/`. GitHub Actions gets it from the secrets `KEYSTORE_B64` and `KS_PASS`; without them CI signs with a throwaway key.

## CI

`.github/workflows/android.yml` has three jobs: **build** (PC tests, signed APK), **smoke** (installs the APK in an Android 34 emulator, starts it, takes a screenshot, fails on a crash) and **release** (only for tags `v*`, publishes `StashReader.apk`).

## What was not tested

Nothing has been run on a real phone. The emulator smoke test only shows that the app starts and draws its screen.
