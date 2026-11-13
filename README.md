# Iridium Control

A modern Android editor and preset librarian for the Strymon Iridium amp & IR cab pedal,
written in Kotlin with Jetpack Compose. It replaces the old *Iridium Controller* app
(Flutter, 2020, targetSdk 30) and works on current phones (Android 8.0 – 15+).

> Independent hobby project, not affiliated with Strymon.

## Features

- **Pedal-style editor** – DRIVE, LEVEL, BASS, MIDDLE, TREBLE and ROOM knobs, AMP
  (round/chime/punch) and CAB (a/b/c) three-way switches, FAV and ON footswitches with LEDs.
  Drag a knob up/right to turn it, double-tap to centre it. Hold FAV to store the sound.
- **Live edit settings** – room size, any of the 9 cabs with any amp, amp bypass,
  volume-pedal pre/post.
- **300-slot preset library** (MIDI banks 0–2) with sync status:
  green = stored in the pedal, amber = only in the app. Copy, rename, clear,
  and save one or many presets into the pedal.
- **Import/export** JSON – also reads files exported by the old Iridium Controller app.
- **Follows the pedal**: with the pedal's MIDI output set to ON, turning the real knobs
  moves the ones in the app.
- **Connections**: USB (pedal's USB port via OTG), any class-compliant USB MIDI
  interface, or Bluetooth LE MIDI adapters. Remembers and auto-connects the last device.
- MIDI monitor and connection test.

## MIDI used

| CC | Function | CC | Function |
|---|---|---|---|
| 0 | Bank (0–2) | 18 | Room size 1–3 |
| 9 | Volume pedal pre/post | 19 | Amp 1–3 |
| 12 | Level | 20 | Cab 0–8 |
| 13 | Drive | 21 | Amp disable |
| 14 | Bass | 27 | FAV footswitch (pedal sends 1 press / 0 release; app sends 127 / 0) |
| 15 | Middle | 28 | ON footswitch (pedal sends 1 press / 0 release; app sends 127 / 0) |
| 16 | Treble | 102 | Bypass (0) / on (127) |
| 17 | Room level | PC | Recall preset |

**Saving into the pedal:** recall the slot (bank + PC), send all CCs, press-and-hold FAV
via CC 27 so the pedal enters save mode, release, then confirm with a Program Change
(firmware 1.33+) or a second FAV press (selectable in Settings, as is the hold time).

## Building

GitHub Actions builds a release APK on every push to `main` (download it from the
run's artifacts) and attaches it to a GitHub release when a `v*` tag is pushed.

To sign with a stable key (so updates install over each other), add these repository
secrets: `SIGNING_KEYSTORE_B64` (base64 of the .jks), `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Without them the APK is debug-signed.

Locally: `./gradlew assembleRelease` (JDK 17, Android SDK 35).
