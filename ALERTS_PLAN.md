# Voice Alert Plan — Speed Limit + Sudden Throttle Jump

Status: **planned, not implemented** (2026-08-16)

## Goal

Spoken (TTS) warnings in the launcher:
1. **Speed limit exceeded** — when vehicle speed crosses a configured limit
2. **Sudden throttle jump** — a stomp on the pedal; a smooth raise produces no warning

## Detection design (cheap by construction)

All checks run on the existing **1 Hz snapshot flow** in `LauncherViewModel`
(a `viewModelScope` collector over `snapshot`) — never in composition.

### Speed limit
- Trigger: `speed.kmh >= SPEED_LIMIT_KMH` (default 100)
- **Hysteresis**: alert arms once; re-arms only after speed drops below `limit − 5` (prevents repeat alerts while hovering at the threshold)
- **Cooldown**: min 30 s between alerts

### Throttle jump
- Track `delta = throttle(t) − throttle(t−1)` (1 s samples)
- Smooth acceleration → small delta → no alert
- Stomp → large delta (≥ `THROTTLE_JUMP`, ~0.35–0.5 of 0..1) → alert
- Same 30 s cooldown; no hysteresis needed (delta-based)

### Throttle source (none in firmware yet)
- Preferred: firmware `THROTTLE` sensor (0–100) → `/100.0`
- Fallback estimate: throttle = `(kmh_delta_per_second / 12).coerceIn(0, 1)` — a +12 km/h jump in one tick ≈ 1.0 (stomp), smooth rise ≈ 0.2 (no alert)

## Voice output

- `TextToSpeech` created in the ViewModel with the Application context
  (`Locale.US`, `QUEUE_FLUSH` so a new alert interrupts the previous)
- Phrases: *"Speed limit exceeded"*, *"Sudden acceleration detected"*
- **Fallback**: if no TTS engine is installed on the unit (common on stripped
  Chinese ROMs), `ToneGenerator` beeps instead (1 long beep = speed,
  2 short = throttle) — never silently fails
- Cleanup: `tts.shutdown()` / `tone.release()` in `onCleared()`

## UI hookup (when real throttle exists)

`MetricsTile.ThrottleSection` currently shows a static 15%: switch the lit
segment count and the % text to `snapshot.throttle` so the heat bar moves live
and the alert is demonstrable.

## Files to touch

| File | Change |
|---|---|
| `data/CarData.kt` | add `throttle: Float = 0.15f` to `CarSnapshot` |
| `data/Esp32DataSource.kt` | parse `THROTTLE` sensor or derive from speed delta |
| `ui/LauncherViewModel.kt` | alert engine (checks, hysteresis, cooldowns) + TTS/tone |
| `ui/components/MetricsTile.kt` | live throttle segments + % text |

## Load impact (T20)

- Detection: integer/float compares at 1 Hz — unmeasurable
- TTS: short synthesis spike (~5–15% CPU for <1 s) **only when an alert fires**;
  cooldowns bound the worst case to 2 alerts/30 s
- No per-frame work, no new threads, no MediaPlayer

## Test plan

1. Emulator: likely no TTS engine → verify tone fallback fires
   (log line on speak; `adb logcat -s LauncherViewModel`)
2. On the unit with the real ESP32: drive speed over the limit → one alert,
   stay above → silence (hysteresis), drop below limit−5 and re-cross → alert again
3. Throttle: gentle speed increase → silence; rapid burst → one alert
4. Long soak: no alert storms, no memory growth (`dumpsys meminfo`)

## Future (after this works)

- Configurable speed limit (settings tile / prefs)
- Additional alerts: door-open while moving, charge/battery warning, engine temp high
- Custom voice packs instead of TTS (pre-recorded WAV/OGG via SoundPool)
