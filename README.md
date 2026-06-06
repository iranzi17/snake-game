# Prince&Phoibe

Prince&Phoibe is a two-player snake game for two Android phones. Both players connect to the same game server, create or join a room code, then compete for the same food while keeping separate scores.

## What is included

- `app/` native Android app source.
- `mobile_snake_server.py` multiplayer room-code server.
- `web/` browser version for phones on the same Wi-Fi.
- `snake_game.py` desktop pygame prototype.
- `.github/workflows/android-release.yml` GitHub Actions build and release workflow.

## Play from Android APK

1. Run the game server on a computer:

   ```powershell
   python .\mobile_snake_server.py --host 0.0.0.0 --port 8000
   ```

2. Install the APK on both Android phones.
3. Open the app and enter the server URL printed by the server, for example:

   ```text
   http://192.168.1.102:8000
   ```

4. One phone creates a room. The other phone joins with the room code.

For internet play, host `mobile_snake_server.py` on a public server or expose port `8000` with a tunnel, then enter that public URL in the Android app.

## Play in a browser

Both phones can also open the server URL directly in Chrome. One phone creates a room and the other joins with the code.

## GitHub release builds

The workflow builds a signed installable APK with GitHub Actions. To publish a release from GitHub:

```powershell
git tag v1.0.0
git push origin main
git push origin v1.0.0
```

GitHub will build the APK and attach it to the `v1.0.0` release. The release also includes a source zip backup of this folder.

## Local Android build

This project can be opened in Android Studio. Local command-line builds need Java, Gradle, and the Android SDK installed:

```powershell
gradle :app:assembleRelease
```
