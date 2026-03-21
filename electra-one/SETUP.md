# Electra One Setup Guide

The Electra One Bitwig controller extension is already implemented and deployed. This document describes what you need to configure on the Electra One hardware and in Bitwig before using it.

---

## Step 1: Upload the Preset to the Electra One

The E1 needs a preset loaded that defines the control layout, CC assignments, and display sections. The preset file is at:

```
electra-one/preset/bitwig-remote-controls.json
```

### How to upload:
1. Open the [Electra One Web Editor](https://app.electra.one) in a browser
2. Connect your Electra One via USB
3. Click **Import** and select `bitwig-remote-controls.json`
4. The preset will upload to the E1

### What the preset defines:
- **36 controls** across 3 control sets (one per E1 section)
- **2x6 grid layout** per section matching the 12 physical knobs
- All controls on **MIDI channel 1**, using **CC 0-11**
- **Section 1**: red parameter faders, white nav faders
- **Section 2**: orange parameter faders, white nav faders
- **Section 3**: blue parameter faders, white nav faders
- All 3 sections share the same CC numbers (the Bitwig extension rebinds on section switch)

---

## Step 2: Configure Bitwig

### Add the controller:
1. Open Bitwig Studio
2. Go to **Settings > Controllers > + Add Controller**
3. Select **Electra One** (vendor: Electra One)

### Assign MIDI ports (if not auto-detected):
The extension requires **2 MIDI input ports** and **2 MIDI output ports**:

| Port | Function | Linux name | Mac/Windows name |
|------|----------|-----------|-----------------|
| Port 1 In/Out | MIDI (CC data) | `Electra Controller Electra Port 1` | `Electra Port 1` |
| Port 2 In/Out | CTRL (SysEx) | `Electra Controller Electra CTRL` | `Electra CTRL` |

Auto-detection is configured for all platforms, so this may happen automatically.

### Optional: Set CC resolution
In the controller's settings panel in Bitwig, there's a **CC Resolution** preference:
- **7-bit** (default): Standard 0-127 range
- **14-bit**: High-resolution 0-16383 range using MSB+LSB CC pairs

If you switch to 14-bit in Bitwig, you should also update the E1 preset controls from `cc7` to `cc14` type for the parameter faders (controls 2-5, 8-11 in each section).

### Optional: Set page filter
The **Page Filter** preference (under "Pages") controls which Remote Controls pages are visible:
- **All Pages** (default): All pages are navigable
- **E1 Only**: Only pages whose name contains "E1" are navigable

When "E1 Only" is active:
- The 3 sections only display matching pages
- Knob 7 (Page) skips to the next/previous page containing "E1"
- Knob 12 (Bank) advances through groups of 3 filtered pages
- Non-matching pages are completely hidden from navigation

To use this, include "E1" somewhere in the Remote Controls page name in Bitwig (e.g., "E1 Filter", "E1 Amp", "E1 Mod"). Pages without "E1" in the name will be skipped.

---

## Step 3: Verify It Works

1. Select an instrument track in Bitwig that has Remote Controls pages
2. **Parameter control**: Turn inner knobs (2-5, 8-11) on E1 - parameters change in Bitwig
3. **Value feedback**: Change a param in Bitwig's UI - E1 knob position updates
4. **Navigation**: Turn corner knobs:
   - Knob 1 (top-left) - prev/next track
   - Knob 6 (top-right) - prev/next device
   - Knob 7 (bottom-left) - prev/next Remote Controls page
   - Knob 12 (bottom-right) - prev/next page bank (shifts all 3 sections by 3)
5. **Display**: E1 screen shows parameter names and formatted values, updates on track/device/page changes
6. **Section switching**: Press Section 1/2/3 buttons on E1 - knobs control that section's page

---

## No Other E1 Configuration Needed

The Electra One firmware does not need any special configuration beyond loading the preset. The controller script handles:
- Bidirectional CC communication on Port 1
- SysEx display updates on Port 2
- Section switch detection via SysEx on Port 2
- All parameter name/value display updates via SysEx `updateRuntime`
