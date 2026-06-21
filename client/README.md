# Android client setup

1. Copy `local.properties.example` to `local.properties`.
2. Set `sdk.dir` to your Android SDK path.
3. Set `API_BASE_URL` to your backend (default `http://10.0.2.2:8080/` for emulator).

The map uses [OpenStreetMap](https://www.openstreetmap.org/) tiles via OSMDroid. No map API key is required.

Build:

```bash
cd client
./gradlew assembleDebug
```
