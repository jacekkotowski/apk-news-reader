# Third-party code

The Android app uses no libraries. Planned for step 2, one vendored file:

| File | What | Licence | Source |
|---|---|---|---|
| `app/assets/Readability.js` | Mozilla Readability, extracts the article text from a web page | Apache-2.0 | https://github.com/mozilla/readability (latest release tag; the exact tag is recorded here when the file is added) |

CI uses the GitHub Action `reactivecircus/android-emulator-runner` (MIT) for the emulator smoke test. It is not part of the APK.
