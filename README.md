# Clawd Desktop Pet

A tiny pixel Clawd that wanders along the bottom of your screen.

```
npm install
npm start
```

- Walks (sometimes runs or hops), idles, blinks and naps on its own
- While idle it looks around, yawns, waves and chats (greetings change with the time of day)
- Click to poke it; click a lot and it gets annoyed, then dizzy
- Drag to pick it up, or flick it: it keeps your throw speed, bounces off the walls and floor, and squashes on landing
- Right-click for a menu (sleep / walk / quit)

## Characters

Besides Clawd there is a second character, **Tung Tung Tung Sahur**, made from a user-supplied FBX model
(rendered once to `assets/tung/front.png` and `side.png`; the mesh has no rig, so all motion is sprite transforms).
Desktop: right-click > Character. Android: pick it in the app before tapping the summon button.

## Android (APK)

`android/` holds an Android version: Clawd floats above other apps with the same behaviour
(walk, idle antics, poke, drag/throw). Grant "Display over other apps" on first launch.

The APK is built by GitHub Actions (`.github/workflows/android.yml`): open the latest run in the
Actions tab and download the `clawd-pet-apk` artifact, then install `app-debug.apk`
(you may need to allow installing from unknown sources). To build locally: `cd android && ./gradlew assembleDebug`.
