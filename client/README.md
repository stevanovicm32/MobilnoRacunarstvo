# Android client setup

1. Copy `local.properties.example` to `local.properties`.
2. Set `sdk.dir` to your Android SDK path.
3. Set `MAPS_API_KEY` to a Google Maps API key with Maps SDK enabled.
4. Set `API_BASE_URL` to your backend (default `http://10.0.2.2:8080/` for emulator).

Build:

```bash
cd client
./gradlew assembleDebug
```
