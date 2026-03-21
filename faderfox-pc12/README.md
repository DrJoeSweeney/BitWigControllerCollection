# Faderfox PC12 — Bitwig Controller Extension

Maps the Faderfox PC12's 72 knobs (CC10–CC81) to Bitwig Remote Controls pages using a naming convention.

## Requirements

- Bitwig Studio 5.x
- Java 12+
- Maven

## Install

### Build from source

```bash
cd faderfox-pc12
mvn clean install
```

This produces `faderfox-pc12.bwextension` and copies it to `../Extensions/`.

### Add the controller in Bitwig

1. Open **Settings → Controllers**
2. Click **+ Add Controller**
3. Select **Faderfox → PC12**
4. Choose your PC12's MIDI input port
5. Click **OK**

## Usage

The extension activates knob mappings based on **Remote Controls page names**. Rename a page to one of the supported patterns and the corresponding knobs will control its 8 parameters.

### Page naming format

| Pattern | Meaning |
|---------|---------|
| `FF1` – `FF9` | Knob group 1–9, responds on **all MIDI channels** |
| `FF1 CH0` – `FF9 CH15` | Knob group 1–9, responds on **one specific MIDI channel** |

Names are case-sensitive. All matching FF pages are active simultaneously — each group of knobs always controls its own page without needing to switch pages in Bitwig. Pages that don't match a pattern are ignored — their corresponding knobs do nothing. If two pages share the same name (e.g., two pages both named `FF1`), only the first one in the page list will be controlled — each group binds to a single page.

### CC mapping

Each group maps to a consecutive block of 8 CCs:

| Page | CCs |
|------|-----|
| FF1  | 10–17 |
| FF2  | 18–25 |
| FF3  | 26–33 |
| FF4  | 34–41 |
| FF5  | 42–49 |
| FF6  | 50–57 |
| FF7  | 58–65 |
| FF8  | 66–73 |
| FF9  | 74–81 |

### Channel filtering

- **`FF3`** — Responds to CC26–33 on any MIDI channel. Use this when you don't care which channel the PC12 is sending on.
- **`FF3 CH5`** — Responds to CC26–33 only on MIDI channel 5 (zero-indexed). Knob input on other channels is ignored.

This lets you configure the PC12 to send on different channels and target specific pages accordingly.

### Example workflow

1. Insert a synth on a track
2. Open the device's **Remote Controls** panel
3. Rename the first page to `FF1`
4. Map the 8 parameters you want to control
5. Turn CC10–17 on your PC12 — the parameters respond
6. Add a second page named `FF2` and map more parameters to CC18–25
7. Turn CC18–25 — the FF2 parameters respond **at the same time** as FF1
8. Add `FF3`, `FF4`, etc. — all groups work simultaneously, no page switching needed

## Multi-Instrument Variant

See `faderfox-pc12-multi/` for a variant that scans all 16 tracks and controls multiple instruments simultaneously using channel-based targeting. Only enable one PC12 variant at a time.

## Notes

- **All pages active at once**: Each FF group has its own independent cursor, so FF1 through FF9 all respond to their knobs simultaneously. No need to select a page before turning knobs.
- **Absolute knobs**: The PC12 sends absolute CC values (0–127). The first twist may cause a parameter jump — this is inherent to absolute pots.
- **Device follows selection**: The extension tracks whichever device is selected on the current track. Selecting a different device automatically updates the mappings.
- **No MIDI output**: The PC12 has no LEDs or displays, so the extension uses 0 MIDI out ports.
- **No auto-detection**: You must manually select the MIDI port when adding the controller in Bitwig.
