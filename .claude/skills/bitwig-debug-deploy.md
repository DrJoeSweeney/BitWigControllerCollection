---
description: Debugging, building, deploying, and troubleshooting Bitwig controller extensions — build commands, Controller Script Console, remote debugging, common errors, MIDI debugging, virtual MIDI ports
triggers:
  - debugging Bitwig extension
  - build errors
  - deploy extension
  - extension not loading
  - MIDI debugging
  - Controller Script Console
  - troubleshooting
  - extension not showing up
  - BITWIG_DEBUG_PORT
  - virtual MIDI
  - snd-virmidi
  - testing without hardware
---

# Bitwig Controller Extension — Debug & Deploy

## Build Commands

```bash
# Full build + deploy to Bitwig Extensions folder
mvn clean install

# Build only (no deploy)
mvn clean package

# Build with verbose output
mvn clean install -X

# Skip tests (if any)
mvn clean install -DskipTests
```

### Build Output
- JAR: `target/<artifactId>.jar`
- Deployed: `~/Bitwig Studio/Extensions/<artifactId>.bwextension` (after `mvn install`)

A `.bwextension` file is just a renamed `.jar`.

---

## Loading in Bitwig

1. Build with `mvn clean install`
2. Open Bitwig Studio
3. **Settings → Controllers → + Add Controller**
4. Your extension appears under the vendor/model from your `ControllerExtensionDefinition`
5. Select the correct MIDI in/out ports
6. The extension activates immediately

### Hot Reload
Bitwig watches the Extensions folder. After rebuilding:
- **Bitwig 4.4+**: Extensions auto-reload when the `.bwextension` file changes
- **Older versions**: Disable and re-enable the controller in Settings, or restart Bitwig

---

## Controller Script Console

The console shows `host.println()` output and error messages.

### How to Open
- **View → Controller Script Console** (or use the keyboard shortcut)
- Each extension gets its own tab in the console
- The console shows:
  - Your `host.println()` messages
  - `host.errorln()` messages
  - Java exceptions and stack traces
  - Extension lifecycle events (init, exit)

### Useful Logging Patterns
```java
// Basic debug output
host.println("Volume: " + cursorTrack.volume().get());

// MIDI message logging
midiIn.setMidiCallback((status, data1, data2) -> {
   host.println(String.format("MIDI IN: %02X %02X %02X (%s)",
      status, data1, data2, describeMidi(status, data1)));
});

// State change logging
transport.isPlaying().addValueObserver(playing -> {
   host.println("Playing: " + playing);
});
```

---

## Remote Debugging (Java Debugger)

Attach a Java debugger (IntelliJ, VS Code, Eclipse) to Bitwig for breakpoints and step-through debugging.

### Setup

1. **Start Bitwig with debug port**:
```bash
# Linux
BITWIG_DEBUG_PORT=5005 bitwig-studio

# Or add to launch script
export BITWIG_DEBUG_PORT=5005
```

2. **Configure IDE** — Remote JVM Debug configuration:
   - Host: `localhost`
   - Port: `5005`
   - Module classpath: your extension module

3. **IntelliJ IDEA**:
   - Run → Edit Configurations → + → Remote JVM Debug
   - Set port to 5005
   - Set "Use module classpath" to your project
   - Start Bitwig, then attach debugger

4. **VS Code** (launch.json):
```json
{
   "type": "java",
   "name": "Attach to Bitwig",
   "request": "attach",
   "hostName": "localhost",
   "port": 5005
}
```

5. Set breakpoints in your extension code and trigger them from the controller.

---

## Common Errors & Fixes

### Extension Not Showing Up in Controller List

| Cause | Fix |
|-------|-----|
| Build failed | Check `mvn clean install` output for errors |
| `.bwextension` not in Extensions folder | Verify `bitwig.extension.directory` in pom.xml matches your OS path |
| "No extensions found" IOException | Missing `META-INF/services/com.bitwig.extension.ExtensionDefinition` file — see below |
| `getRequiredAPIVersion()` too high | Lower it to match your `extension-api` dependency version |
| Class loading error | Check Controller Script Console for stack traces |
| Duplicate UUID | Ensure your extension UUID is unique |

### "No extensions found" — Missing ServiceLoader Registration

Bitwig uses Java's ServiceLoader to discover extensions inside `.bwextension` (JAR) files. If the service provider file is missing, Bitwig logs:

```
Error scanning extension file xxx.bwextension:
    java.io.IOException: No extensions found in ...xxx.bwextension
```

**Fix**: Create the file `src/main/resources/META-INF/services/com.bitwig.extension.ExtensionDefinition` containing the fully qualified class name of your `ControllerExtensionDefinition` subclass:

```
com.example.mycontroller.MyControllerExtensionDefinition
```

Then rebuild with `mvn clean install`. This file **must** be present in every extension JAR.

### Extension Loads but Doesn't Work

| Symptom | Cause | Fix |
|---------|-------|-----|
| Values always 0 | Missing `markInterested()` | Call `.markInterested()` on every value you read |
| LEDs don't update | Sending MIDI in `init()` | Move all MIDI output to `flush()` |
| MIDI not received | Wrong port index | Verify `getNumMidiInPorts()` and port selection in Bitwig |
| Observer not firing | Subscribed after `init()` | All observers must be registered in `init()` |
| Crash on load | Exception in `init()` | Check Console for stack trace, add try/catch around init code |
| Faders/knobs don't respond | Wrong MIDI channel | Channels are 0-indexed in the API (0 = ch1, 9 = ch10) |

### API Version Mismatch
```
Error: Extension requires API version 18 but Bitwig provides 17
```
- Either update Bitwig Studio or lower `getRequiredAPIVersion()` and the `extension-api` dependency version in pom.xml.

### Build Errors

| Error | Fix |
|-------|-----|
| `Cannot resolve com.bitwig:extension-api` | Ensure the Bitwig Maven repo is in pom.xml (`https://maven.bitwig.com`) |
| `source/target 12 not supported` | Install JDK 12+ and set `JAVA_HOME` |
| `package com.bitwig.extension does not exist` | Check dependency version matches an actual release |

---

## MIDI Debugging Techniques

### Log All MIDI Input
```java
// Add to init() — logs every MIDI message
midiIn.setMidiCallback((status, data1, data2) -> {
   host.println(String.format("MIDI: status=0x%02X data1=%d data2=%d", status, data1, data2));
   // ... your normal handling
});

midiIn.setSysexCallback(data -> {
   host.println("SYSEX: " + data);
});
```

### External MIDI Monitors

#### Linux
```bash
# List MIDI ports
aconnect -l

# Monitor raw MIDI input from a specific port
aseqdump -p "My Controller"

# Monitor all MIDI
aseqdump -p 0

# Send test MIDI message
amidi -p hw:1,0 -S "90 3C 7F"   # Note On, middle C, velocity 127
```

#### Cross-Platform
- **MIDI Monitor** (macOS): Snoize MIDI Monitor
- **MIDI-OX** (Windows): General MIDI utility
- **Protokol** (all platforms): Modern MIDI/OSC monitor

---

## Testing Without Hardware

### Virtual MIDI Ports (Linux)

```bash
# Load virtual MIDI kernel module
sudo modprobe snd-virmidi

# Verify — should show virtual MIDI ports
aconnect -l | grep -i virtual

# Create more virtual ports if needed
sudo modprobe snd-virmidi midi_devs=4
```

Virtual MIDI ports appear in Bitwig's MIDI port list. Connect your extension to them, then use `amidi` or another tool to send test messages.

### Send Test MIDI from Command Line
```bash
# Find the virtual MIDI port
amidi -l

# Send Note On (middle C, velocity 100)
amidi -p hw:2,0 -S "90 3C 64"

# Send CC (CC 7, value 64)
amidi -p hw:2,0 -S "B0 07 40"

# Send Note Off
amidi -p hw:2,0 -S "80 3C 00"
```

### Virtual MIDI on Other Platforms
- **macOS**: IAC Driver (built-in, enable in Audio MIDI Setup)
- **Windows**: loopMIDI by Tobias Erichsen (free)

---

## Development Workflow

### Recommended Iteration Loop

1. **Edit** code in your IDE
2. **Build**: `mvn clean install`
3. **Check** Controller Script Console for errors
4. **Test** with your hardware or virtual MIDI
5. **Repeat**

### Tips

- Keep the Controller Script Console open at all times during development
- Add a version/build timestamp to your `init()` println to confirm reloads:
  ```java
  host.println("MyController v1.0 loaded at " + java.time.LocalTime.now());
  ```
- Use `host.showPopupNotification("message")` for quick visual feedback
- Start simple: get one button working before building out the full mapping
- Test MIDI I/O independently before adding complex logic
- Use `host.scheduleTask(runnable, delayMs)` for delayed operations (but prefer observers)

---

## Project Structure Checklist

```
my-controller/
├── pom.xml                              ✓ Bitwig Maven repo configured
├── src/main/java/.../                   ✓ Definition + Extension classes
├── target/*.jar                         ✓ Build output
└── ~/Bitwig Studio/Extensions/*.bwextension  ✓ Deployed
```

Verify each step:
```bash
# 1. Build succeeds
mvn clean install

# 2. Extension file exists
ls -la ~/Bitwig\ Studio/Extensions/*.bwextension

# 3. Bitwig recognizes it
# Check Settings → Controllers → + Add Controller

# 4. Console shows init message
# View → Controller Script Console
```
