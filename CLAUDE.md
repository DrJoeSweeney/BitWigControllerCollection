# Bitwig Controller Extensions

This project develops custom controller extensions for Bitwig Studio.

## Language & Build

- **Language**: Java (not legacy JavaScript)
- **Java version**: 12+
- **Build**: `mvn clean install`
- **API dependency**: `com.bitwig:extension-api` from `https://maven.bitwig.com`

## Key Conventions

- Register all observers and subscriptions in `init()`
- Send all MIDI output in `flush()` — never in `init()` or observers
- Call `.markInterested()` on every value you intend to read
- Clean up hardware state (LEDs, displays) in `exit()`

## Deploy Target

- `../Extensions/` (relative to the module directory, i.e. `<repo>/Extensions/`)

## Extensions

- **`faderfox-pc12/`** — Faderfox PC12: follows the selected device on the current track. One instrument at a time.
- **`faderfox-pc12-multi/`** — Faderfox PC12 MultiInstrument: scans first 16 tracks, controls multiple instruments simultaneously via channel-based targeting (`FFn CHc`). Only enable one PC12 variant at a time.
- **`electra-one/`** — Electra One: follows selected device, maps 8 inner knobs to Remote Controls parameters, 4 corner knobs for navigation (track/device/page/bank). 3-section display shows consecutive control pages. Bidirectional: SysEx display updates + CC value feedback. Supports 7-bit and 14-bit CC. Optional "E1 Only" page filter to restrict navigation to pages containing "E1" in their name. 2 MIDI ports (MIDI + CTRL).

## Skills

See `.claude/skills/` for detailed guidance:
- `bitwig-project-setup.md` — Scaffolding, Maven config, project structure
- `bitwig-controller-api.md` — Core API: MIDI, transport, tracks, devices, observers
- `bitwig-hardware-surface.md` — Hardware Surface API: controls, layers, LEDs
- `bitwig-debug-deploy.md` — Building, debugging, troubleshooting, virtual MIDI
