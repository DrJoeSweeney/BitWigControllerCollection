---
description: Bitwig Hardware Surface API — physical controls (buttons, knobs, sliders, LEDs), MIDI bindings, action matchers, layers for mode switching, LED feedback, and controller layout
triggers:
  - Hardware Surface API
  - physical controls
  - hardware buttons
  - hardware knobs
  - hardware sliders
  - hardware LEDs
  - multi-state light
  - layers
  - hardware bindings
  - controller layout
  - action matcher
  - encoder mode
  - relative encoder
  - onUpdateHardware
  - setBounds
---

# Bitwig Hardware Surface API

The Hardware Surface API (API 12+) provides a structured way to model physical controller hardware. Instead of raw MIDI callbacks, you declare hardware elements (buttons, knobs, LEDs) and bind them to Bitwig actions. This enables Bitwig to display a controller visualization and manage mode switching via layers.

## Overview

```java
// In init()
HardwareSurface surface = host.createHardwareSurface();

// 1. Create hardware elements (buttons, knobs, sliders, lights)
// 2. Bind MIDI messages to those elements (action matchers)
// 3. Create layers and bind elements to Bitwig actions per layer
// 4. Activate the default layer
```

---

## Creating Hardware Elements

### Buttons
```java
HardwareButton playButton = surface.createHardwareButton("PLAY");

// Bind to MIDI: Note On, channel 1, note 41
playButton.pressedAction().setActionMatcher(
   midiIn.createNoteOnActionMatcher(0, 41)
);

// Optional: bind release
playButton.releasedAction().setActionMatcher(
   midiIn.createNoteOffActionMatcher(0, 41)
);
```

### Absolute Knobs (Pots with fixed range 0–127)
```java
AbsoluteHardwareKnob volumeKnob = surface.createAbsoluteHardwareKnob("VOLUME");

// Bind to CC 7 on channel 1
volumeKnob.setAdjustValueMatcher(
   midiIn.createAbsoluteCCValueMatcher(0, 7)
);
```

### Relative Encoders
```java
RelativeHardwareKnob encoder = surface.createRelativeHardwareKnob("ENCODER_1");

// Different encoder modes — choose the one matching your hardware:

// 2's complement (most common): 1-63 = clockwise, 65-127 = counter-clockwise
encoder.setAdjustValueMatcher(
   midiIn.createRelative2sComplementCCValueMatcher(0, 16, 128)
);

// Signed bit: 1-63 = CW, 65-127 = CCW (bit 6 = direction)
encoder.setAdjustValueMatcher(
   midiIn.createRelativeSignedBitCCValueMatcher(0, 16, 128)
);

// Binary offset: 64 = center (no movement), <64 = CCW, >64 = CW
encoder.setAdjustValueMatcher(
   midiIn.createRelativeBinOffsetCCValueMatcher(0, 16, 128)
);

// The last parameter (128) is the sensitivity/resolution
```

### Sliders / Faders
```java
HardwareSlider fader = surface.createHardwareSlider("FADER_1");

// Bind to CC 0 on channel 1
fader.setAdjustValueMatcher(
   midiIn.createAbsoluteCCValueMatcher(0, 0)
);

// For pitch bend (14-bit range):
fader.setAdjustValueMatcher(
   midiIn.createAbsolutePitchBendValueMatcher(0)
);
```

---

## Action Matchers Reference

Action matchers define which MIDI messages trigger a hardware element.

```java
// Note matchers (channel is 0-indexed)
midiIn.createNoteOnActionMatcher(channel, note)
midiIn.createNoteOffActionMatcher(channel, note)

// CC matchers
midiIn.createAbsoluteCCValueMatcher(channel, cc)
midiIn.createRelative2sComplementCCValueMatcher(channel, cc, stepSize)
midiIn.createRelativeSignedBitCCValueMatcher(channel, cc, stepSize)
midiIn.createRelativeBinOffsetCCValueMatcher(channel, cc, stepSize)

// Pitch bend
midiIn.createAbsolutePitchBendValueMatcher(channel)

// Custom expression matcher for complex cases
midiIn.createActionMatcher("status == 0xB0 && data1 == 0x10 && data2 > 0")
```

---

## Lights & LED Feedback

### Single-Color On/Off Light
```java
OnOffHardwareLight light = surface.createOnOffHardwareLight("PLAY_LED");
playButton.setBackgroundLight(light);

// In a layer binding (see Layers below):
// The light state is automatically derived from the bound target.

// Or set manually:
light.onUpdateHardware(() -> {
   // Called during flush — send MIDI to update the physical LED
   midiOut.sendMidi(0x90, 41, light.isOn().currentValue() ? 127 : 0);
});
```

### Multi-State Light (multiple colors / brightness levels)
```java
MultiStateHardwareLight light = surface.createMultiStateHardwareLight("PAD_LED_1");

// Define a function that maps internal state to a visual color:
light.setColorToStateFunction(color -> {
   // Map Color to an integer state your hardware understands
   if (color == null) return 0;
   // Return a MIDI value representing this color
   return colorToVelocityMap.getOrDefault(color, 0);
});

light.state().onUpdateHardware(state -> {
   // Send the state value as MIDI
   int value = state != null ? (int) state : 0;
   midiOut.sendMidi(0x90, padNote, value);
});
```

---

## Layers — Mode Switching

Layers let you define different bindings for the same physical controls. Only the active layer's bindings are in effect.

```java
// In init()
Layers layers = new Layers(this);

Layer mainLayer = new Layer(layers, "Main");
Layer mixLayer = new Layer(layers, "Mix");
Layer deviceLayer = new Layer(layers, "Device");

// --- Main Layer bindings ---
mainLayer.bindPressed(playButton, transport.playAction());
mainLayer.bindPressed(stopButton, transport.stopAction());
mainLayer.bindPressed(recordButton, transport.recordAction());

// Bind knob to parameter (absolute)
mainLayer.bind(volumeKnob, cursorTrack.volume());

// Bind relative encoder to parameter
mainLayer.bind(encoder1, remoteControls.getParameter(0));

// Bind fader
mainLayer.bind(fader1, cursorTrack.volume());

// --- Mix Layer bindings ---
for (int i = 0; i < 8; i++) {
   mixLayer.bind(faders[i], trackBank.getItemAt(i).volume());
   mixLayer.bind(knobs[i], trackBank.getItemAt(i).pan());
   mixLayer.bindPressed(muteButtons[i], trackBank.getItemAt(i).mute().toggleAction());
}

// --- Device Layer bindings ---
for (int i = 0; i < 8; i++) {
   deviceLayer.bind(knobs[i], remoteControls.getParameter(i));
}

// Activate default layer
mainLayer.activate();

// Mode switching (e.g., from a button callback)
// mainLayer is always active, swap between mix and device:
mixButton -> {
   deviceLayer.deactivate();
   mixLayer.activate();
}
deviceButton -> {
   mixLayer.deactivate();
   deviceLayer.activate();
}
```

### Layer Binding Methods
```java
// Button bindings
layer.bindPressed(button, action);           // HardwareActionBindable
layer.bindPressed(button, runnable);         // Runnable
layer.bindReleased(button, action);
layer.bindToggle(button, settableBooleanValue); // Toggle on press, LED follows state

// Knob/slider bindings
layer.bind(absoluteKnob, parameter);         // AbsoluteHardwareKnob → SettableRangedValue
layer.bind(relativeKnob, parameter);         // RelativeHardwareKnob → SettableRangedValue
layer.bind(slider, parameter);               // HardwareSlider → SettableRangedValue

// Light bindings (explicit)
layer.bindLightState(light, booleanSupplier); // OnOffHardwareLight
layer.bindLightState(light, intSupplier);     // MultiStateHardwareLight
```

---

## Physical Layout (Controller Visualization)

Define the physical layout so Bitwig can display a visual representation of your controller.

```java
// Set overall controller size (in millimeters)
surface.setPhysicalSize(300, 200);

// Position each element (x, y, width, height in mm)
playButton.setBounds(10, 170, 15, 10);
stopButton.setBounds(30, 170, 15, 10);

for (int i = 0; i < 8; i++) {
   faders[i].setBounds(10 + i * 25, 30, 20, 120);
   knobs[i].setBounds(10 + i * 25, 5, 20, 20);
}
```

---

## Combining with Raw MIDI

You can use the Hardware Surface API alongside raw MIDI callbacks. This is useful for handling messages that don't map to simple controls (e.g., sysex displays, complex LED protocols).

```java
// In init()
// Hardware surface handles buttons/knobs/faders
HardwareSurface surface = host.createHardwareSurface();
// ... set up surface elements ...

// Raw callback handles everything else
midiIn.setMidiCallback((status, data1, data2) -> {
   // Note: messages consumed by hardware surface elements
   // will NOT appear here. Only unmatched messages arrive.
   handleRawMidi(status, data1, data2);
});

midiIn.setSysexCallback(data -> {
   handleSysex(data);
});
```

**Important**: MIDI messages that match a hardware element's action matcher are consumed and won't reach the raw callback. Design accordingly.

---

## Complete Example — 8-Knob Controller

```java
@Override
public void init()
{
   final ControllerHost host = getHost();
   final MidiIn midiIn = host.getMidiInPort(0);
   final MidiOut midiOut = host.getMidiOutPort(0);

   // Device
   CursorTrack cursorTrack = host.createCursorTrack("CURSOR", "Cursor", 0, 0, true);
   CursorDevice cursorDevice = cursorTrack.createCursorDevice();
   CursorRemoteControlsPage remotes = cursorDevice.createCursorRemoteControlsPage(8);

   // Hardware surface
   HardwareSurface surface = host.createHardwareSurface();
   Layers layers = new Layers(this);
   Layer mainLayer = new Layer(layers, "Main");

   for (int i = 0; i < 8; i++) {
      RelativeHardwareKnob knob = surface.createRelativeHardwareKnob("KNOB_" + i);
      knob.setAdjustValueMatcher(
         midiIn.createRelative2sComplementCCValueMatcher(0, i, 128)
      );

      RemoteControl param = remotes.getParameter(i);
      param.markInterested();
      param.name().markInterested();

      mainLayer.bind(knob, param);
   }

   mainLayer.activate();
   host.println("8-Knob Controller initialized");
}
```
