# Third-party code

The Android app uses no libraries. One vendored file:

| File | What | Licence | Source |
|---|---|---|---|
| `app/assets/Readability.js` | Mozilla Readability, extracts the article text from a web page (reader mode) | Apache-2.0 (licence text in `THIRD_PARTY_Readability_LICENSE.md`) | https://github.com/mozilla/readability, tag `0.6.0` (the newest tag on 2026-11-22; the project publishes no GitHub releases), SHA-256 `34dcab3d0832d0019f02990eed6b6124e029e8c32b9f0c6f2550544ff8dff174` |

CI uses the GitHub Action `reactivecircus/android-emulator-runner` (MIT) for the emulator smoke test. It is not part of the APK.
