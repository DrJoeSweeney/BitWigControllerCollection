# Electra One Setup Guide

The Electra One Bitwig controller extension maps the E1's 12 knobs (2 rows x 6) across 3 touchscreen control sets to show 3 consecutive Remote Controls pages of the selected device.

---

## Knob Layout (per control set)

```
Row A: [A1 Track] [A2 P0] [A3 P1] [A4 P2] [A5 P3] [A6 Device]
Row B: [B1 Page]  [B2 P4] [B3 P5] [B4 P6] [B5 P7] [B6 Bank]
```

- **A2-A5, B2-B5** (8 inner knobs): Control the 8 parameters of that set's page
- **A1**: Navigate tracks (prev/next)
- **A6**: Navigate devices in chain (prev/next)
- **B1**: Shift all 3 sets by 1 page (prev/next)
- **B6**: Bank jump — shift all 3 sets by 3 pages

## 3 Control Sets

The E1 touchscreen shows 3 control sets (swipe to switch):
- **Set 1** (red): Current page (page N)
- **Set 2** (orange): Next page (page N+1)
- **Set 3** (blue): Page N+2

Swiping between sets on the E1 touchscreen rebinds the physical knobs to that set's page.

---

## Step 1: Upload the Preset to the Electra One

The preset file is at `electra-one/preset/bitwig-remote-controls.json`.

1. Open the [Electra One Web Editor](https://app.electra.one)
2. Connect your Electra One via USB
3. Click **Import** and select `bitwig-remote-controls.json`

The preset defines 36 controls (12 per set) on MIDI channel 1, CC 0-11.

---

## Step 2: Configure Bitwig

### Add the controller:
1. Open Bitwig Studio
2. Go to **Settings > Controllers > + Add Controller**
3. Select **Electra One** (vendor: Electra One)

### Assign MIDI ports (if not auto-detected):

| Port | Function | Linux name | Mac/Windows name |
|------|----------|-----------|-----------------|
| Port 1 In/Out | MIDI (CC data) | `Electra Controller Electra Port 1` | `Electra Port 1` |
| Port 2 In/Out | CTRL (SysEx) | `Electra Controller Electra CTRL` | `Electra CTRL` |

### Optional: Set CC resolution
- **7-bit** (default): Standard 0-127 range
- **14-bit**: High-resolution 0-16383 using MSB+LSB CC pairs

### Optional: Set page filter
- **All Pages** (default): All pages are navigable
- **E1 Only**: Only pages whose name contains "E1" are navigable

---

## Step 3: Verify It Works

1. Select an instrument track with Remote Controls pages
2. **Parameters**: Turn inner knobs (A2-A5, B2-B5) — values change in Bitwig
3. **Feedback**: Change a param in Bitwig's UI — E1 knob position updates
4. **Navigation**: Turn corner knobs (A1=track, A6=device, B1=page, B6=bank)
5. **Display**: E1 screen shows parameter names and values per control set
6. **Touchscreen**: Swipe between sets — knobs rebind to that set's page
