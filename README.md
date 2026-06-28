# Mobilno Računarstvo

Koriscena je Java 17 pa je potrebno prebaciti terminal na istu.

Kompajliranje .apk
```
./gradlew assembleDebug
```

Instaliranje na povezani telefon
```
/opt/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
cd
```

Pokretanje servera
```
go run cmd/server/main.go
```