# Faderfox PC12 MultiInstrument

A Bitwig Studio controller extension for the Faderfox PC12 that controls instruments across **multiple tracks simultaneously**, rather than following a single selected device.

## How It Works

The extension scans the first **16 tracks** in your project. Each track's first instrument device is monitored for remote control pages matching the `FF` naming convention.

### Page Naming

- **`FF1`** through **`FF9`** — Maps to knob groups 1–9 (CCs 10–17, 18–25, … 74–81). Binds on **all 16 MIDI channels**.
- **`FF1 CH0`** through **`FF9 CH15`** — Same group mapping, but binds on **only the specified MIDI channel**.

### Channel-Based Targeting

Channel suffixes allow different tracks to share the same FF group number without conflict:

| Track | Page Name  | Effect |
|-------|-----------|--------|
| 1     | `FF1 CH0` | CCs 10–17 on channel 0 control Track 1's instrument |
| 5     | `FF1 CH2` | CCs 10–17 on channel 2 control Track 5's instrument |

Without a channel suffix (`FF1`), all 16 channels are bound to that track's instrument for that group.

### Limits

- Monitors the first **16 tracks** only (Bitwig TrackBank size).
- **9 groups × 8 knobs** = 72 CCs per channel (CC 10–81).
- Only the **first instrument** on each track is targeted (`FIRST_INSTRUMENT` follow mode).

## Setup

1. Build: `mvn clean install` (deploys `.bwextension` to `../Extensions/`)
2. In Bitwig: Settings → Controllers → Add → "Faderfox PC12 MultiInstrument"
3. Select the PC12's MIDI input port

## Important

Only enable **one** PC12 extension at a time — either `faderfox-pc12` (single device, follows selection) or `faderfox-pc12-multi` (multi-track). Running both simultaneously will cause binding conflicts.
