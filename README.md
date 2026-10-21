# Norway Shop Converter — Android APK

Android app for tourists: Norwegian shelf-label camera OCR, Norwegian-to-English ML Kit translation, NOK-to-GBP exchange rate updates, offline saved rate and basket.

## Build on a phone using GitHub

1. Sign in to GitHub in Chrome and create a **private** repository (for example `norway-shop-converter`).
2. Upload **the contents** of this ZIP to the repository, keeping `.github/workflows/build-apk.yml` and `app/` in their directories. GitHub's browser upload can have difficulty uploading folders from mobile; the easiest option is to use a mobile Git client that can import a ZIP and push its directory tree, or upload individual files through GitHub's web interface.
3. Open the repository's **Actions** tab. Choose **Build Android APK** → **Run workflow**. A push to `main` also starts the build.
4. Open the completed workflow, tap the **Norway-Shop-Converter-APK** artifact, and download it. Extract the ZIP to get `app-debug.apk`.
5. Tap `app-debug.apk` and approve Android's **Install unknown apps** permission for the app opening the APK. Disable this permission again afterward if desired.

The APK is debug-signed and intended for personal testing, not Play Store release. Never enter passwords or payment details into a build from untrusted sources.

## Important limitations

- Camera capture uses the Android camera application's **thumbnail**, not full-resolution live scanning or augmented-reality overlays. Small shelf text may be missed.
- OCR runs locally using bundled Latin text recognition. The translation model needs to download once; after that it can run offline.
- The app attempts an exchange-rate update when opened, and you can tap to refresh. Offline mode uses the saved rate, with a labelled starter rate until the first successful update.
- Price detection chooses the first decimal-looking price. Review the result carefully before adding to basket.
- The project has not been compiled or tested on a physical phone in this environment. GitHub Actions will report any build errors.
- The rate API is `open.er-api.com`, a third-party service. Internet is required to update rates.
