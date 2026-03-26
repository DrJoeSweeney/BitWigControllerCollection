# Electra One — Bitwig Remote Controls Setup

## What This Does

This extension turns your Electra One into a Bitwig Remote Controls surface.
The E1's 3 touchscreen control sets each show a different Remote Controls page
from the currently selected device. The Bitwig extension dynamically updates
the E1 display with parameter names, values, and colors via SysEx — you don't
need to configure anything on the E1 beyond loading the preset.

### Encoder Layout

```
Row A: [A1 Page]   [A2 P0] [A3 P1] [A4 P2] [A5 P3] [A6 Track]
Row B: [B1 Device] [B2 P4] [B3 P5] [B4 P6] [B5 P7] [B6 Volume]
```

| Knob | Function |
|------|----------|
| A1   | Rotate through the device's Remote Controls pages |
| A2–A5 | Parameters 0–3 of the active page |
| A6   | Select next/previous track |
| B1   | Select next/previous device on the track |
| B2–B5 | Parameters 4–7 of the active page |
| B6   | Rotate = track volume. Touch = play/stop toggle |

### 3 Control Sets

| Set | Color | Shows |
|-----|-------|-------|
| 1   | Red   | Current page (base page) |
| 2   | Orange | Next page |
| 3   | Blue  | Page after that |

Pages wrap when the device has more than 3 pages. Touching a set on the E1
screen switches which set the physical encoders control.

---

## Prerequisites

- **Electra One firmware 3.4 or later** (required for pot-touch events used
  by the B6 play/stop toggle). Check your firmware version under
  E1 Menu → Info. Update via the [Electra One Web Editor](https://app.electra.one/)
  if needed.
- **Java 12+** and **Maven** for building the Bitwig extension.
- **Bitwig Studio** with Extension API 18+.

---

## Step 1 — Load the Preset onto the Electra One

The preset file is at `preset/bitwig-remote-controls.json` in this repository.
It defines 36 controls (12 per control set × 3 sets) on MIDI Channel 1.

> **Note:** The preset labels ("Param 0", "Param 1", etc.) are just placeholders.
> Once the Bitwig extension connects, it overwrites every label and color on
> the E1 screen dynamically via SysEx. You never need to edit the preset
> manually to match your instruments.

### Using the Electra One Web Editor

1. Open <https://app.electra.one/> in Chrome or Edge (requires Web MIDI).
2. Connect your E1 to your computer via USB.
3. In the editor, click your E1's name in the top bar to confirm it's connected.
4. Click **Menu (☰) → Import** and select `bitwig-remote-controls.json`.
5. The editor shows the preset with 3 control sets. You should see:
   - **Set 1:** 4 white nav controls at the corners, 8 red parameter controls
   - **Set 2:** Same layout, orange parameter controls
   - **Set 3:** Same layout, blue parameter controls

   Switch between sets using the set buttons below the control grid.
6. Click **Menu (☰) → Send to Electra** to upload the preset to a slot.
   Choose any available preset slot (e.g., Bank 1 / Slot 1).
7. On the E1's touchscreen, navigate to that preset slot and select it so it
   becomes the active preset. You should see the placeholder labels on the
   E1 screen.

### Using the Electra One Hardware Directly (alternative)

If you prefer not to use the web editor:

1. Copy `bitwig-remote-controls.json` to a USB drive.
2. On the E1, go to **Menu → USB Host → Import preset** and select the file.
3. Activate the preset by selecting it on the E1 screen.

### What the Preset Contains

```
Control Set 1 (Red)          Control Set 2 (Orange)       Control Set 3 (Blue)
┌──────┬──────┬──────┬──────┬──────┬──────┐ ┌──────┬──────┬──────┬──────┬──────┬──────┐ ┌──────┬──────┬──────┬──────┬──────┬──────┐
│ Page │  P0  │  P1  │  P2  │  P3  │Track │ │ Page │  P0  │  P1  │  P2  │  P3  │Track │ │ Page │  P0  │  P1  │  P2  │  P3  │Track │
│CC  0 │CC  1 │CC  2 │CC  3 │CC  4 │CC  5 │ │CC  0 │CC  1 │CC  2 │CC  3 │CC  4 │CC  5 │ │CC  0 │CC  1 │CC  2 │CC  3 │CC  4 │CC  5 │
├──────┼──────┼──────┼──────┼──────┼──────┤ ├──────┼──────┼──────┼──────┼──────┼──────┤ ├──────┼──────┼──────┼──────┼──────┼──────┤
│Device│  P4  │  P5  │  P6  │  P7  │ Vol  │ │Device│  P4  │  P5  │  P6  │  P7  │ Vol  │ │Device│  P4  │  P5  │  P6  │  P7  │ Vol  │
│CC  6 │CC  7 │CC  8 │CC  9 │CC 10 │CC 11 │ │CC  6 │CC  7 │CC  8 │CC  9 │CC 10 │CC 11 │ │CC  6 │CC  7 │CC  8 │CC  9 │CC 10 │CC 11 │
└──────┴──────┴──────┴──────┴──────┴──────┘ └──────┴──────┴──────┴──────┴──────┴──────┘ └──────┴──────┴──────┴──────┴──────┴──────┘
```

All 3 sets use the same CC numbers (0–11) on MIDI Channel 1. This is by design —
the extension tracks which set is active and sends/receives CC values accordingly.
The E1 automatically routes the physical encoders to whichever set you select.

---

## Step 2 — Build the Bitwig Extension

```bash
cd electra-one
mvn clean install
```

This compiles the extension and copies it to `../Extensions/electra-one.bwextension`.
Bitwig auto-discovers `.bwextension` files from that directory on startup.

If Bitwig is already running, you can reload extensions from
**Settings → Controllers** (remove and re-add the controller).

---

## Step 3 — Configure Bitwig

### Add the Controller

1. Open **Bitwig Studio → Settings → Controllers**.
2. Click **+ Add Controller**.
3. Search for **"Electra One"** (vendor: Electra One).
4. Click **Add**.

### Assign MIDI Ports

The E1 exposes two MIDI port pairs over USB. If auto-detection doesn't work,
assign them manually:

| Bitwig Port | What It Carries | Linux Name | Mac / Windows Name |
|-------------|-----------------|------------|-------------------|
| Input 1 / Output 1 | CC data (parameter values, navigation) | `Electra Controller Electra Port 1` | `Electra Port 1` |
| Input 2 / Output 2 | SysEx (display updates, events) | `Electra Controller Electra CTRL` | `Electra CTRL` |

> **Important:** If Port 2 is not assigned to the CTRL port, the E1 screen will
> not update and section switching / play-stop touch will not work.

---

## Step 4 — Verify Everything Works

1. **Select a track** in Bitwig that has an instrument or audio effect with
   Remote Controls pages (e.g., Polymer, Polysynth, any third-party plugin).
2. **Click on the device** in Bitwig's device chain to select it.
3. **Check the E1 screen** — within ~2 seconds, Set 1 should show the device's
   first Remote Controls page with parameter names, current values, and red
   colour-coding. Sets 2 and 3 show the next two pages (orange and blue).
4. **Turn an inner knob** (A2–A5 or B2–B5) — the parameter should change
   in Bitwig's UI.
5. **Change a parameter in Bitwig** (mouse drag or automation) — the E1
   knob value should follow.
6. **Touch a different control set** on the E1 screen — the physical encoders
   should switch to that set's page.
7. **Turn A1** — the displayed pages should rotate through the device's
   Remote Controls pages.
8. **Turn A6** — the selected track in Bitwig should change.
9. **Turn B1** — the selected device on the track should change.
10. **Turn B6** — the track volume should change.
11. **Touch B6** (brief tap) — transport should toggle play/stop.

---

## Troubleshooting

| Symptom | Check |
|---------|-------|
| E1 screen doesn't update at all | Verify Bitwig Port 2 is assigned to `Electra CTRL` (not `Electra Port 1`). Check the Bitwig controller console for "Electra One v2.0 initialized". |
| Parameter knobs don't respond | Ensure the `Bitwig Remote Controls` preset is the active preset on the E1. Verify MIDI Channel 1 in both the E1 preset device settings and Bitwig. |
| Navigation knobs (A1/A6/B1/B6) don't work or jump erratically | These expect relative CC values (2's complement). The preset's fader controls send absolute CC by default. If you experience issues, you can edit the preset in the E1 Web Editor: select each nav control, open its settings, and change the encoder mode to "Relative (2's complement)". |
| Play/stop on B6 touch doesn't work | Requires E1 firmware 3.4+. Also confirm the extension subscribed to events — look for "Subscribed to E1 events" in Bitwig's controller console (Settings → Controllers → your controller → console icon). |
| Wrong parameters shown | Make sure you've clicked on (selected) the target device in Bitwig's device chain. The extension follows Bitwig's device selection. |
| Display flickers during page changes | This is normal for large batch updates. The extension disables E1 repaint during bulk SysEx sends and re-enables it after, so you should see a single clean refresh. |

---

## Reference

### MIDI CC Map

All on MIDI Channel 1.

| CC | Knob | Role | Encoding |
|----|------|------|----------|
| 0  | A1   | Page navigation | Relative (1–63 CW, 65–127 CCW) |
| 1  | A2   | Parameter 0 | Absolute (0–127) |
| 2  | A3   | Parameter 1 | Absolute |
| 3  | A4   | Parameter 2 | Absolute |
| 4  | A5   | Parameter 3 | Absolute |
| 5  | A6   | Track navigation | Relative |
| 6  | B1   | Device navigation | Relative |
| 7  | B2   | Parameter 4 | Absolute |
| 8  | B3   | Parameter 5 | Absolute |
| 9  | B4   | Parameter 6 | Absolute |
| 10 | B5   | Parameter 7 | Absolute |
| 11 | B6   | Track volume | Relative |

### SysEx Messages (CTRL Port)

| Direction | Command | Purpose |
|-----------|---------|---------|
| → E1 | `F0 00 21 45 14 07 [id] {json} F7` | Set control name and color |
| → E1 | `F0 00 21 45 14 0E [id] 00 <text> F7` | Set displayed value text |
| → E1 | `F0 00 21 45 7F 7A [01\|00] F7` | Enable/disable display repaint |
| → E1 | `F0 00 21 45 14 79 29 F7` | Subscribe to E1 events |
| ← E1 | `F0 00 21 45 7E 07 [set] F7` | Control set switched (1–3) |
| ← E1 | `F0 00 21 45 7E 0A [pot] [id] [touch] F7` | Pot touch event |

### E1 Control IDs

| Set | Row A (IDs) | Row B (IDs) |
|-----|-------------|-------------|
| 1   | 1, 2, 3, 4, 5, 6 | 7, 8, 9, 10, 11, 12 |
| 2   | 13, 14, 15, 16, 17, 18 | 19, 20, 21, 22, 23, 24 |
| 3   | 25, 26, 27, 28, 29, 30 | 31, 32, 33, 34, 35, 36 |
