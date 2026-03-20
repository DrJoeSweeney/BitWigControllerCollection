---
description: Core Bitwig Controller API reference — MIDI I/O, transport, tracks, devices, clips, parameters, observers, note input, preferences, and lifecycle rules
triggers:
  - writing controller logic
  - MIDI handling in Bitwig
  - MIDI input callback
  - MIDI output
  - transport control
  - track bank
  - cursor track
  - device parameters
  - remote controls
  - clip launcher
  - scene launching
  - note input
  - observers and subscriptions
  - value observer
  - markInterested
  - preferences
  - document state
  - sysex
---

# Bitwig Controller API — Core Reference

## Extension Lifecycle

### `init()`
Called once when the extension is activated. **All setup goes here**:
- Create MIDI ports, note inputs
- Subscribe to values (observers)
- Create transport, track banks, cursor tracks, devices
- Set up hardware surface elements
- Register MIDI callbacks

**Never send MIDI output from `init()`**. The hardware state is undefined until the first `flush()`.

### `flush()`
Called by Bitwig whenever the extension should synchronize state to hardware. **All outgoing MIDI goes here**:
- LED updates
- Display text
- Motorized fader positions
- Any MIDI output to the controller

Bitwig calls `flush()` after any subscribed value changes. You can also request it via `host.requestFlush()`.

### `exit()`
Called when the extension is deactivated. Clean up:
- Reset LEDs to off
- Clear displays
- Release any resources

---

## MIDI I/O

### Getting Ports
```java
// In init()
MidiIn midiIn = host.getMidiInPort(0);    // First MIDI in port
MidiOut midiOut = host.getMidiOutPort(0);  // First MIDI out port

// For multi-port controllers:
MidiIn midiIn2 = host.getMidiInPort(1);   // Second MIDI in port
```
Port indices correspond to `getNumMidiInPorts()` / `getNumMidiOutPorts()` in your definition.

### Receiving MIDI — Raw Callback
```java
// In init()
midiIn.setMidiCallback((int status, int data1, int data2) -> {
   // status: message type + channel (e.g., 0x90 = Note On ch1, 0xB0 = CC ch1)
   // data1:  note number or CC number (0-127)
   // data2:  velocity or CC value (0-127)

   host.println("MIDI: " + status + " " + data1 + " " + data2);
});

// For sysex:
midiIn.setSysexCallback((String data) -> {
   // data is a hex string like "F0 7E 7F 06 01 F7"
   host.println("Sysex: " + data);
});
```

### MIDI Message Anatomy
```
Status byte = message type (high nibble) + channel (low nibble)
  0x80 = Note Off       0x90 = Note On        0xA0 = Aftertouch
  0xB0 = Control Change 0xC0 = Program Change  0xD0 = Channel Pressure
  0xE0 = Pitch Bend

Examples:
  0x90, 60, 127  →  Note On, channel 1, middle C, velocity 127
  0xB0, 7, 64    →  CC 7 (volume), channel 1, value 64
  0xB3, 1, 100   →  CC 1 (mod wheel), channel 4, value 100
```

### Sending MIDI
```java
// In flush()
midiOut.sendMidi(0x90, 60, 127);   // Note On
midiOut.sendMidi(0xB0, 7, 64);     // CC

// Sysex (hex string, must start with F0 and end with F7)
midiOut.sendSysex("F0 00 20 6B 7F 42 02 00 10 01 F7");
```

---

## NoteInput — Keyboard/Pad Pass-Through

NoteInput lets MIDI messages pass directly to Bitwig's engine (instruments) without going through your callback, reducing latency.

```java
// In init()
// Pass all MIDI directly to Bitwig as note input:
NoteInput noteInput = midiIn.createNoteInput("Keys");

// With filter masks — only pass specific messages:
// Each mask is a hex string: "SS DD DD" where ? is wildcard
NoteInput padInput = midiIn.createNoteInput("Pads",
   "89????",   // Note Off channel 10
   "99????",   // Note On channel 10
   "A9????"    // Aftertouch channel 10
);

// If you also want these messages in your MIDI callback:
padInput.setShouldConsumeEvents(false);
```

### Filter Mask Format
- 2-character hex pairs, `?` = wildcard
- `"80????"` = Note Off, any channel (low nibble of 8x), any note, any velocity
- `"B0CC??"` where CC is a specific CC number in hex

---

## Transport

```java
// In init()
Transport transport = host.createTransport();

// Control
transport.play();
transport.stop();
transport.record();
transport.togglePlay();
transport.isPlaying().markInterested();
transport.isRecording().markInterested();
transport.isArrangerRecordEnabled().markInterested();

// Tempo
transport.tempo().markInterested();
transport.tempo().addValueObserver(tempo -> {
   // tempo is in BPM (as raw value, use .displayedValue() for formatted)
});

// Position
transport.getPosition().markInterested();
transport.getPosition().addValueObserver(pos -> {
   // pos is in beats
});

// Metronome
transport.isMetronomeEnabled().toggleAction();

// Punch in/out
transport.isPunchInEnabled().toggle();
transport.isPunchOutEnabled().toggle();

// Loop
transport.isArrangerLoopEnabled().toggle();
```

---

## Track Bank & Cursor Track

### Cursor Track
A cursor track follows the currently selected track in Bitwig.

```java
// In init()
CursorTrack cursorTrack = host.createCursorTrack("MY_CURSOR", "Cursor", 0, 0, true);

// Parameters (all SettableRangedValue, 0.0–1.0)
cursorTrack.volume().markInterested();
cursorTrack.pan().markInterested();
cursorTrack.mute().markInterested();
cursorTrack.solo().markInterested();
cursorTrack.arm().markInterested();

// Modify
cursorTrack.volume().set(0.75);
cursorTrack.mute().toggle();

// Track name
cursorTrack.name().markInterested();
cursorTrack.name().addValueObserver(name -> {
   host.println("Track: " + name);
});

// Navigation
cursorTrack.selectPrevious();
cursorTrack.selectNext();
```

### Track Bank
A track bank gives access to a fixed-size window of tracks (for controllers with multiple channel strips).

```java
// In init()
// 8 tracks, 0 sends, 8 scenes
TrackBank trackBank = host.createTrackBank(8, 0, 8);

// Or follow the cursor track:
TrackBank trackBank = cursorTrack.createSiblingsTrackBank(8, 0, 8, false);

for (int i = 0; i < 8; i++) {
   Track track = trackBank.getItemAt(i);
   track.volume().markInterested();
   track.pan().markInterested();
   track.mute().markInterested();
   track.solo().markInterested();
   track.arm().markInterested();
   track.name().markInterested();
   track.exists().markInterested();

   // Clip launcher
   ClipLauncherSlotBank slots = track.clipLauncherSlotBank();
   slots.setIndication(true); // Show slot indicators in Bitwig UI
   for (int s = 0; s < 8; s++) {
      ClipLauncherSlot slot = slots.getItemAt(s);
      slot.hasContent().markInterested();
      slot.isPlaying().markInterested();
      slot.isRecording().markInterested();
      slot.isQueued().markInterested();
   }
}

// Scrolling
trackBank.scrollPageForwards();
trackBank.scrollPageBackwards();
trackBank.canScrollForwards().markInterested();
trackBank.canScrollBackwards().markInterested();
```

### Scene Launching
```java
SceneBank sceneBank = trackBank.sceneBank();
for (int i = 0; i < 8; i++) {
   Scene scene = sceneBank.getItemAt(i);
   scene.name().markInterested();
   scene.exists().markInterested();
}
// Launch scene
sceneBank.getItemAt(0).launch();
// Launch slot on specific track
trackBank.getItemAt(0).clipLauncherSlotBank().getItemAt(0).launch();
```

---

## Devices & Remote Controls

```java
// In init()
CursorDevice cursorDevice = cursorTrack.createCursorDevice(
   "MY_DEVICE", "Device", 0,
   CursorDeviceFollowMode.FOLLOW_SELECTION
);

cursorDevice.name().markInterested();
cursorDevice.exists().markInterested();

// Remote controls page (typically 8 knobs)
CursorRemoteControlsPage remoteControls =
   cursorDevice.createCursorRemoteControlsPage(8);

remoteControls.pageNames().markInterested();
remoteControls.selectedPageIndex().markInterested();

for (int i = 0; i < 8; i++) {
   RemoteControl param = remoteControls.getParameter(i);
   param.markInterested();
   param.name().markInterested();
   param.displayedValue().markInterested();

   // Set value (0.0 to 1.0)
   // param.set(0.5);
   // Increment (useful for relative encoders)
   // param.inc(1, 128);  // increment by 1 step out of 128
}

// Navigate pages
remoteControls.selectPreviousPage(true);
remoteControls.selectNextPage(true);

// Navigate devices
cursorDevice.selectPrevious();
cursorDevice.selectNext();

// Enter/exit device chains (nested devices)
cursorDevice.isNested().markInterested();
cursorDevice.selectParent();
cursorDevice.selectFirstInSlot(0);
```

---

## Observer Pattern — Critical Rules

### `markInterested()`
You **must** call `markInterested()` on any value you want to read. Without it, Bitwig won't send updates and the value will be stale/zero.

```java
// WRONG — value will always be 0
double vol = cursorTrack.volume().get();

// RIGHT
cursorTrack.volume().markInterested();
// ... later, in flush() or callback:
double vol = cursorTrack.volume().get();  // Now returns actual value
```

### Value Observers
```java
// Boolean
transport.isPlaying().addValueObserver(isPlaying -> {
   // Called on Bitwig's control surface thread when value changes
   needsUpdate = true;  // Set a flag, update hardware in flush()
});

// String
cursorTrack.name().addValueObserver(name -> { /* ... */ });

// Integer
remoteControls.selectedPageIndex().addValueObserver(index -> { /* ... */ });

// Double (ranged 0.0–1.0)
cursorTrack.volume().addValueObserver(vol -> { /* ... */ });

// Formatted string for display
cursorTrack.volume().displayedValue().addValueObserver(text -> { /* ... */ });
```

### Subscription Timing
- Subscribe to everything in `init()`. You cannot add observers after init completes.
- Observers fire on Bitwig's control surface thread — they are **not** on the MIDI thread.
- Observer callbacks and `flush()` are never called concurrently (thread-safe within the extension).

---

## Preferences & Document State

### User Preferences (persist across projects)
```java
// In init()
Preferences prefs = host.getPreferences();
SettableEnumValue mode = prefs.getEnumSetting("Mode", "General",
   new String[]{"Default", "Advanced"}, "Default");
mode.markInterested();

SettableBooleanValue ledFeedback = prefs.getBoolSetting("LED Feedback", "General", true);
SettableRangedValue brightness = prefs.getNumberSetting("Brightness", "Display", 0, 100, 1, "", 80);
SettableStringValue customText = prefs.getStringSetting("Custom Text", "Display", 20, "Hello");
```

### Document State (persist per-project)
```java
DocumentState docState = host.getDocumentState();
SettableEnumValue projectMode = docState.getEnumSetting("Mode", "My Controller",
   new String[]{"Mix", "Edit"}, "Mix");
```

These settings appear in Bitwig's controller settings panel.

---

## Notifications & Logging

```java
// Print to Controller Script Console
host.println("Debug message");

// Show popup notification in Bitwig UI
host.showPopupNotification("My Controller: Mode changed");

// Error logging
host.errorln("Something went wrong!");
```

---

## Common Patterns

### Debounced Flush
```java
private boolean needsFullUpdate = false;

// In an observer:
needsFullUpdate = true;

// In flush():
if (needsFullUpdate) {
   // Update all LEDs, displays, etc.
   needsFullUpdate = false;
}
```

### Channel Strip Index Mapping
```java
// Map hardware fader index to track bank track
for (int i = 0; i < 8; i++) {
   final int index = i;  // Must be final for lambda
   Track track = trackBank.getItemAt(i);
   track.volume().addValueObserver(vol -> {
      faderValues[index] = (int)(vol * 127);
   });
}
```

---

## Reference Links

- **Bitwig Extension API source**: https://github.com/bitwig/bitwig-extensions
- **DrivenByMoss** (large reference implementation): https://github.com/git-moss/DrivenByMoss
- **Bitwig Extension Hub**: Community extensions and examples
- **API Javadoc**: Available in the `extension-api` jar (attach sources in your IDE)
