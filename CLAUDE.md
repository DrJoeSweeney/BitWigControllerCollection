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
- **`electra-one/`** — Electra One: follows selected device, 3 touchscreen control sets show 3 consecutive Remote Controls pages. 8 inner knobs (A2-A5, B2-B5) per set for parameters, 4 corner knobs for navigation (A1=track, A6=device, B1=page, B6=bank). Bidirectional SysEx display updates + CC value feedback. Supports 7-bit and 14-bit CC. Optional "E1 Only" page filter. 2 MIDI ports (MIDI + CTRL).
- **`note-fx-scale/`** — Note FX Scale Quantizer: MIDI note processor that quantizes incoming notes to a selected scale/mode. 49 scales (diatonic, harmonic/melodic minor modes, pentatonic, blues, symmetric, world, bebop, chromatic), 60 variations (degree subsets: triads, 7ths, sus2/4, power, etc.), 4 snap directions (nearest, round up, round down, pass through). All settings MIDI-learnable via DocumentState. Uses NoteInput with key translation table — no MIDI output port needed.

## Skills

See `.claude/skills/` for detailed guidance:
- `bitwig-project-setup.md` — Scaffolding, Maven config, project structure
- `bitwig-controller-api.md` — Core API: MIDI, transport, tracks, devices, observers
- `bitwig-hardware-surface.md` — Hardware Surface API: controls, layers, LEDs
- `bitwig-debug-deploy.md` — Building, debugging, troubleshooting, virtual MIDI
