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

- Linux: `~/Bitwig Studio/Extensions/`
- macOS: `~/Documents/Bitwig Studio/Extensions/`
- Windows: `%USERPROFILE%/Documents/Bitwig Studio/Extensions/`

## Skills

See `.claude/skills/` for detailed guidance:
- `bitwig-project-setup.md` — Scaffolding, Maven config, project structure
- `bitwig-controller-api.md` — Core API: MIDI, transport, tracks, devices, observers
- `bitwig-hardware-surface.md` — Hardware Surface API: controls, layers, LEDs
- `bitwig-debug-deploy.md` — Building, debugging, troubleshooting, virtual MIDI
