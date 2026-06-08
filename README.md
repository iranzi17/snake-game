# Prince&Phoibe

Prince&Phoibe is a snake game for Android phones. You can play alone offline, or two players can connect to the same game server, create or join a room code, then compete for the same food while keeping separate scores.

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https%3A%2F%2Fgithub.com%2Firanzi17%2Fsnake-game)

## What is included

- `app/` native Android app source.
- `mobile_snake_server.py` multiplayer room-code server.
- `web/` browser version for phones on the same Wi-Fi.
- `snake_game.py` desktop pygame prototype.
- `.github/workflows/android-release.yml` GitHub Actions build and release workflow.
- `render.yaml` Render Blueprint for free online hosting.

## Play from Android APK

To play alone, install the APK and tap `Play Alone`. Solo mode runs fully on the phone and does not need internet, a server, or another player.

To play with two phones online, deploy the server to Render first. The app defaults to:

```text
https://prince-phoibe-snake.onrender.com
```

If Render gives your service a different URL, enter that URL in the app.

## Host on Render

1. Click the `Deploy to Render` button above.
2. Sign in to Render with GitHub.
3. Review the `prince-phoibe-snake` free web service.
4. Click deploy and wait until the service is live.
5. Use the Render service URL in both Android phones.

Render free services can sleep after inactivity, so the first room creation after a break can take a little longer.

## Host on your computer

To play with two phones on the same Wi-Fi without Render:

1. Run the game server on a computer:

   ```powershell
   python .\mobile_snake_server.py --host 0.0.0.0 --port 8000
   ```

2. Install the APK on both Android phones.
3. Open the app and enter the current phone URL printed by the server, for example:

   ```text
   http://192.168.1.107:8000
   ```

4. One phone creates a room. The other phone joins with the room code.

For internet play, host `mobile_snake_server.py` on a public server or expose port `8000` with a tunnel, then enter that public URL in the Android app.

If room creation fails, make sure the computer and phones are on the same Wi-Fi, the Python server is still running, and Windows Firewall allows Python to accept connections.

## Play in a browser

Both phones can also open the server URL directly in Chrome. One phone creates a room and the other joins with the code.

## GitHub release builds

The workflow builds a signed installable APK with GitHub Actions. To publish a release from GitHub:

```powershell
git tag v1.0.4
git push origin main
git push origin v1.0.4
```

GitHub will build the APK and attach it to the release tag. The release also includes a source zip backup of this folder.

## Local Android build

This project can be opened in Android Studio. Local command-line builds need Java, Gradle, and the Android SDK installed:

```powershell
gradle :app:assembleRelease
```
