package com.electraone.bitwig;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDevice;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.RemoteControl;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;

import static com.electraone.bitwig.ElectraOneMidiConfig.*;

/**
 * Electra One controller extension for Bitwig Studio (v3).
 *
 * 3 E1 touchscreen sections show 3 consecutive Remote Controls pages from
 * the selected device. A complete E1 preset is uploaded on init.
 *
 * Layout per control set:
 *   Row A: [A1 Page-LIST] [A2 P0] [A3 P1] [A4 P2] [A5 P3] [A6 Track-LIST]
 *   Row B: [B1 Device-LIST] [B2 P4] [B3 P5] [B4 P6] [B5 P7] [B6 Volume]
 *
 * Navigation (A1, A6, B1) uses list controls — user selects by name.
 * B6 remains a fader for track volume, with play/stop on touch.
 */
public class ElectraOneExtension extends ControllerExtension
{
   private ControllerHost host;
   private MidiOut midiOut;
   private ElectraOneSysEx sysEx;
   private Transport transport;

   private CursorTrack cursorTrack;
   private CursorDevice cursorDevice;

   // Banks for list enumeration
   private TrackBank trackBank;
   private DeviceBank deviceBank;
   private static final int NUM_TRACKS = 16;
   private static final int NUM_DEVICES = 16;

   // Cached names for lists
   private final String[] trackNames = new String[NUM_TRACKS];
   private final String[] deviceNames = new String[NUM_DEVICES];

   // 3 independent page cursors — one per E1 control set / section
   private CursorRemoteControlsPage[] sectionPages;

   // Which E1 control set is currently active (0-2)
   private int activeSection = 0;

   // Page navigation state
   private int basePage = 0;
   private int pageCount = 0;
   private String[] cachedPageNames = {};

   // Cached navigation labels
   private String trackName = "";
   private String deviceName = "";

   // Display change detection (per section × param)
   private boolean needsFullUpdate = true;
   private final String[][] lastSentName = new String[NUM_SECTIONS][NUM_PARAMS];
   private final String[][] lastSentDisplayValue = new String[NUM_SECTIONS][NUM_PARAMS];

   // CC value feedback change detection
   private final int[][] lastSentCC = new int[NUM_SECTIONS][NUM_PARAMS];

   // Nav display change detection
   private String lastNavVolume = "";
   private boolean lastNavPlaying = false;

   // Echo suppression
   private static final long ECHO_SUPPRESS_MS = 250;
   private final long[][] lastHardwareTime = new long[NUM_SECTIONS][NUM_PARAMS];

   // Volume is now absolute CC — no accumulator needed

   // Track which lists need updating
   private boolean pageListDirty = true;
   private boolean trackListDirty = true;
   private boolean deviceListDirty = true;

   // SysEx heartbeat spam filter
   private int heartbeatCount = 0;

   // E1 overlay IDs
   private static final int OVERLAY_PAGES   = 1;
   private static final int OVERLAY_TRACKS  = 2;
   private static final int OVERLAY_DEVICES = 3;

   protected ElectraOneExtension(
         ElectraOneExtensionDefinition definition, ControllerHost host)
   {
      super(definition, host);
   }

   // ── Init ──────────────────────────────────────────────────────────────

   @Override
   public void init()
   {
      host = getHost();

      // MIDI ports
      MidiIn midiIn   = host.getMidiInPort(PORT_MIDI);
      MidiIn ctrlIn   = host.getMidiInPort(PORT_CTRL);
      midiOut          = host.getMidiOutPort(PORT_MIDI);
      MidiOut ctrlOut  = host.getMidiOutPort(PORT_CTRL);
      sysEx = new ElectraOneSysEx(ctrlOut, host);

      // Transport
      transport = host.createTransport();
      transport.isPlaying().markInterested();
      transport.isPlaying().addValueObserver(playing -> host.requestFlush());

      // Track bank (16 tracks) for track list
      trackBank = host.createTrackBank(NUM_TRACKS, 0, 0);
      for (int i = 0; i < NUM_TRACKS; i++)
      {
         Track track = trackBank.getItemAt(i);
         track.name().markInterested();
         track.exists().markInterested();
         final int idx = i;
         trackNames[i] = "";
         track.name().addValueObserver(name ->
         {
            trackNames[idx] = name;
            trackListDirty = true;
            host.requestFlush();
         });
      }

      // Cursor track
      cursorTrack = host.createCursorTrack("E1_CURSOR", "E1 Track", 0, 0, true);
      cursorTrack.name().markInterested();
      cursorTrack.name().addValueObserver(name ->
      {
         trackName = name;
         host.requestFlush();
      });
      cursorTrack.volume().markInterested();
      cursorTrack.volume().displayedValue().markInterested();
      cursorTrack.volume().displayedValue().addValueObserver(v -> host.requestFlush());

      // Cursor device (follows Bitwig selection)
      cursorDevice = cursorTrack.createCursorDevice(
         "E1_DEVICE", "E1 Device", 0, CursorDeviceFollowMode.FOLLOW_SELECTION);
      cursorDevice.exists().markInterested();
      cursorDevice.name().markInterested();
      cursorDevice.hasNext().markInterested();
      cursorDevice.hasPrevious().markInterested();
      cursorDevice.name().addValueObserver(name ->
      {
         deviceName = name;
         basePage = 0;
         needsFullUpdate = true;
         host.requestFlush();
      });

      // Device bank (16 devices) on cursor track for device list
      deviceBank = cursorTrack.createDeviceBank(NUM_DEVICES);
      for (int i = 0; i < NUM_DEVICES; i++)
      {
         Device dev = deviceBank.getDevice(i);
         dev.name().markInterested();
         dev.exists().markInterested();
         final int idx = i;
         deviceNames[i] = "";
         dev.name().addValueObserver(name ->
         {
            deviceNames[idx] = name;
            deviceListDirty = true;
            host.requestFlush();
         });
      }

      // 3 section pages — independent cursors on the same device
      sectionPages = new CursorRemoteControlsPage[NUM_SECTIONS];
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         sectionPages[s] = cursorDevice.createCursorRemoteControlsPage(
            "E1_SEC_" + s, NUM_PARAMS, "");
         sectionPages[s].selectedPageIndex().markInterested();
         sectionPages[s].pageNames().markInterested();

         if (s == 0)
         {
            sectionPages[0].pageNames().addValueObserver(names ->
            {
               cachedPageNames = names != null ? names : new String[0];
               pageCount = cachedPageNames.length;
               if (basePage >= pageCount && pageCount > 0)
               {
                  basePage = 0;
               }
               updateAllSectionPageIndices();
               pageListDirty = true;
               needsFullUpdate = true;
               host.requestFlush();
            });

            sectionPages[0].selectedPageIndex().addValueObserver(index ->
            {
               if (index >= 0 && index != basePage)
               {
                  basePage = index;
                  updateAllSectionPageIndices();
                  needsFullUpdate = true;
                  host.requestFlush();
               }
            });
         }

         for (int p = 0; p < NUM_PARAMS; p++)
         {
            RemoteControl param = sectionPages[s].getParameter(p);
            param.markInterested();
            param.name().markInterested();
            param.displayedValue().markInterested();
            param.value().markInterested();
            param.name().addValueObserver(n -> host.requestFlush());
            param.displayedValue().addValueObserver(v -> host.requestFlush());
            param.value().addValueObserver(v -> host.requestFlush());
         }
      }

      updateAllSectionPageIndices();

      // MIDI callback
      midiIn.setMidiCallback(this::onMidi);
      ctrlIn.setSysexCallback(this::onSysEx);
      sysEx.subscribeToEvents();

      // Initialize change-detection caches
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            lastSentName[s][i] = "\0";
            lastSentDisplayValue[s][i] = "\0";
            lastSentCC[s][i] = -1;
         }
      }

      // Upload E1 preset and do initial list updates after Bitwig populates observers
      host.scheduleTask(() ->
      {
         sysEx.uploadPreset(buildPresetJson());
         host.scheduleTask(() ->
         {
            needsFullUpdate = true;
            pageListDirty = true;
            trackListDirty = true;
            deviceListDirty = true;
            host.requestFlush();
         }, 1500);
      }, 500);

      host.println("Electra One v3.0 initialized (list navigation)");
   }

   // ── Page navigation ───────────────────────────────────────────────────

   private void selectPage(int pageIndex)
   {
      if (pageIndex < 0 || pageIndex >= pageCount) return;
      basePage = pageIndex;
      updateAllSectionPageIndices();
      needsFullUpdate = true;
      host.requestFlush();
   }

   private void updateAllSectionPageIndices()
   {
      if (pageCount <= 0) return;
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         int pageIdx = (basePage + s) % pageCount;
         sectionPages[s].selectedPageIndex().set(pageIdx);
      }
   }

   // ── MIDI input (port 0) ──────────────────────────────────────────────

   private void onMidi(int status, int data1, int data2)
   {
      if ((status & 0xF0) != 0xB0) return;
      if ((status & 0x0F) != CHANNEL) return;

      switch (data1)
      {
         case CC_PAGE:
         {
            // Absolute CC 0-127 → scale to page index
            if (pageCount <= 0) return;
            int pageIdx = (data2 * pageCount) / 128;
            if (pageIdx >= pageCount) pageIdx = pageCount - 1;
            selectPage(pageIdx);
            return;
         }

         case CC_TRACK:
         {
            // Absolute CC 0-127 → scale to track index (0..15)
            int trackCount = countExistingTracks();
            if (trackCount <= 0) return;
            int trackIdx = (data2 * trackCount) / 128;
            if (trackIdx >= trackCount) trackIdx = trackCount - 1;
            trackBank.getItemAt(trackIdx).selectInMixer();
            return;
         }

         case CC_DEVICE:
         {
            // Absolute CC 0-127 → scale to device index
            int devCount = countExistingDevices();
            if (devCount <= 0) return;
            int devIdx = (data2 * devCount) / 128;
            if (devIdx >= devCount) devIdx = devCount - 1;
            deviceBank.getDevice(devIdx).selectInEditor();
            return;
         }

         case CC_VOLUME:
            // Absolute CC 0-127 → direct volume mapping
            cursorTrack.volume().set(data2 / 127.0);
            return;
      }

      // Parameter knobs — absolute CC
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            if (data1 == SECTION_PARAM_CC[s][i])
            {
               lastHardwareTime[s][i] = System.currentTimeMillis();
               double normalized = data2 / 127.0;
               sectionPages[s].getParameter(i).set(normalized);
               return;
            }
         }
      }
   }

   // ── SysEx input (CTRL port) ──────────────────────────────────────────

   private void onSysEx(String data)
   {
      if (data.contains("7e000000") || data.contains("7E000000"))
      {
         heartbeatCount++;
         return;
      }

      int section = ElectraOneSysEx.parseSectionSwitch(data);
      if (section >= 0 && section < NUM_SECTIONS)
      {
         if (section != activeSection)
         {
            activeSection = section;
            host.requestFlush();
         }
         return;
      }

      int touchPotId = ElectraOneSysEx.parsePotTouch(data);
      if (touchPotId == POT_B6)
      {
         transport.togglePlay();
         return;
      }
   }

   // ── Flush (display + CC feedback + list updates) ──────────────────

   @Override
   public void flush()
   {
      // Update dynamic lists when dirty
      if (pageListDirty)
      {
         pageListDirty = false;
         sysEx.updateOverlay(OVERLAY_PAGES, cachedPageNames);
         sysEx.setListValue(CC_PAGE, basePage);
      }
      if (trackListDirty)
      {
         trackListDirty = false;
         String[] names = buildTrackNameList();
         sysEx.updateOverlay(OVERLAY_TRACKS, names);
      }
      if (deviceListDirty)
      {
         deviceListDirty = false;
         String[] names = buildDeviceNameList();
         sysEx.updateOverlay(OVERLAY_DEVICES, names);
      }

      boolean batch = needsFullUpdate;
      if (batch)
      {
         needsFullUpdate = false;
         sysEx.setRepaintEnabled(false);

         for (int s = 0; s < NUM_SECTIONS; s++)
         {
            for (int i = 0; i < NUM_PARAMS; i++)
            {
               lastSentName[s][i] = "\0";
               lastSentDisplayValue[s][i] = "\0";
               lastSentCC[s][i] = -1;
            }
         }
         lastNavVolume = "\0";
         lastNavPlaying = !transport.isPlaying().get();
      }

      // Parameter display updates
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            RemoteControl param = sectionPages[s].getParameter(i);
            String name = param.name().get();
            String displayValue = param.displayedValue().get();

            if (!name.equals(lastSentName[s][i])
                  || !displayValue.equals(lastSentDisplayValue[s][i]))
            {
               lastSentName[s][i] = name;
               lastSentDisplayValue[s][i] = displayValue;
               int ctrlId = getParamControlId(s, i);
               sysEx.sendControlNameColor(ctrlId, name, SECTION_COLORS[s]);
               sysEx.sendValueLabel(ctrlId, displayValue);
            }
         }
      }

      // Volume + play state display updates
      updateNavDisplay();

      // CC value feedback
      long now = System.currentTimeMillis();
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            double val = sectionPages[s].getParameter(i).get();
            int cc7 = (int) Math.round(val * 127.0);

            if (now - lastHardwareTime[s][i] < ECHO_SUPPRESS_MS)
            {
               lastSentCC[s][i] = cc7;
               continue;
            }

            if (cc7 != lastSentCC[s][i])
            {
               lastSentCC[s][i] = cc7;
               midiOut.sendMidi(0xB0 | CHANNEL, SECTION_PARAM_CC[s][i], cc7);
            }
         }
      }

      if (batch)
      {
         sysEx.setRepaintEnabled(true);
      }
   }

   private void updateNavDisplay()
   {
      String volValue = cursorTrack.volume().displayedValue().get();
      boolean playing = transport.isPlaying().get();

      if (!volValue.equals(lastNavVolume) || playing != lastNavPlaying)
      {
         lastNavVolume = volValue;
         lastNavPlaying = playing;
         String volColor = playing ? PLAY_COLOR : NAV_COLOR;

         for (int s = 0; s < NUM_SECTIONS; s++)
         {
            sysEx.sendControlNameColor(
               getNavControlId(s, NAV_VOLUME_OFFSET), "Volume", volColor);
            sysEx.sendValueLabel(
               getNavControlId(s, NAV_VOLUME_OFFSET), volValue);
         }
      }
   }

   // ── List name builders ────────────────────────────────────────────────

   private String[] buildTrackNameList()
   {
      int count = 0;
      for (int i = 0; i < NUM_TRACKS; i++)
      {
         if (trackBank.getItemAt(i).exists().get() &&
             trackNames[i] != null && !trackNames[i].isEmpty())
            count = i + 1;
      }
      if (count == 0) return new String[] { "---" };
      String[] result = new String[count];
      for (int i = 0; i < count; i++)
      {
         result[i] = (trackNames[i] != null && !trackNames[i].isEmpty())
            ? trackNames[i] : ("Track " + (i + 1));
      }
      return result;
   }

   private String[] buildDeviceNameList()
   {
      int count = 0;
      for (int i = 0; i < NUM_DEVICES; i++)
      {
         if (deviceBank.getDevice(i).exists().get() &&
             deviceNames[i] != null && !deviceNames[i].isEmpty())
            count = i + 1;
      }
      if (count == 0) return new String[] { "---" };
      String[] result = new String[count];
      for (int i = 0; i < count; i++)
      {
         result[i] = (deviceNames[i] != null && !deviceNames[i].isEmpty())
            ? deviceNames[i] : ("Device " + (i + 1));
      }
      return result;
   }

   // ── E1 Preset builder ────────────────────────────────────────────────

   private String buildPresetJson()
   {
      StringBuilder sb = new StringBuilder(4096);
      sb.append("{\"version\":2,\"name\":\"Bitwig E1\",\"projectId\":\"bitwig-e1-v3\",");

      // Pages (3 sections)
      sb.append("\"pages\":[");
      sb.append("{\"id\":1,\"name\":\"Set 1\"},");
      sb.append("{\"id\":2,\"name\":\"Set 2\"},");
      sb.append("{\"id\":3,\"name\":\"Set 3\"}],");

      // Device (virtual device for CC routing)
      sb.append("\"devices\":[{\"id\":1,\"name\":\"Bitwig\",\"instrumentId\":\"bitwig\",\"port\":1,\"channel\":1}],");

      // Overlays (placeholders — updated dynamically via Lua)
      sb.append("\"overlays\":[");
      sb.append("{\"id\":1,\"items\":[{\"value\":0,\"label\":\"---\"}]},");
      sb.append("{\"id\":2,\"items\":[{\"value\":0,\"label\":\"---\"}]},");
      sb.append("{\"id\":3,\"items\":[{\"value\":0,\"label\":\"---\"}]}],");

      // Controls: 12 per page × 3 pages = 36 controls
      sb.append("\"controls\":[");
      boolean first = true;
      for (int page = 0; page < 3; page++)
      {
         int pageId = page + 1;
         int baseId = page * CONTROLS_PER_SECTION + 1;

         // A1 — Page list
         if (!first) sb.append(","); first = false;
         sb.append(listControl(baseId + NAV_PAGE_OFFSET, pageId,
            "Page", CC_PAGE, OVERLAY_PAGES, 0, 0));

         // A2-A5 — Parameters 0-3
         for (int p = 0; p < 4; p++)
         {
            sb.append(",");
            sb.append(faderControl(baseId + PARAM_OFFSET[p], pageId,
               "P" + (p + 1), SECTION_PARAM_CC[page][p], p + 1, 0));
         }

         // A6 — Track list
         sb.append(",");
         sb.append(listControl(baseId + NAV_TRACK_OFFSET, pageId,
            "Track", CC_TRACK, OVERLAY_TRACKS, 5, 0));

         // B1 — Device list
         sb.append(",");
         sb.append(listControl(baseId + NAV_DEVICE_OFFSET, pageId,
            "Device", CC_DEVICE, OVERLAY_DEVICES, 0, 1));

         // B2-B5 — Parameters 4-7
         for (int p = 4; p < 8; p++)
         {
            sb.append(",");
            sb.append(faderControl(baseId + PARAM_OFFSET[p], pageId,
               "P" + (p + 1), SECTION_PARAM_CC[page][p], p - 3, 1));
         }

         // B6 — Volume fader
         sb.append(",");
         sb.append(faderControl(baseId + NAV_VOLUME_OFFSET, pageId,
            "Volume", CC_VOLUME, 5, 1));
      }
      sb.append("]}");
      return sb.toString();
   }

   private static final int COL_W = 170;
   private static final int ROW_H = 95;
   private static final int PAD = 4;

   private String listControl(int id, int pageId, String name, int cc,
                              int overlayId, int col, int row)
   {
      int x = col * COL_W + PAD;
      int y = row * ROW_H + 40 + PAD;
      int w = COL_W - PAD * 2;
      int h = ROW_H - PAD * 2;
      return "{\"id\":" + id + ",\"type\":\"list\",\"name\":\"" + name
         + "\",\"color\":\"FFFFFF\",\"pageId\":" + pageId
         + ",\"bounds\":[" + x + "," + y + "," + w + "," + h + "]"
         + ",\"values\":[{\"id\":\"value\",\"overlayId\":" + overlayId
         + ",\"message\":{\"deviceId\":1,\"type\":\"cc7\""
         + ",\"parameterNumber\":" + cc + ",\"min\":0,\"max\":127}}]}";
   }

   private String faderControl(int id, int pageId, String name, int cc,
                               int col, int row)
   {
      int x = col * COL_W + PAD;
      int y = row * ROW_H + 40 + PAD;
      int w = COL_W - PAD * 2;
      int h = ROW_H - PAD * 2;
      return "{\"id\":" + id + ",\"type\":\"fader\",\"name\":\"" + name
         + "\",\"pageId\":" + pageId
         + ",\"bounds\":[" + x + "," + y + "," + w + "," + h + "]"
         + ",\"values\":[{\"id\":\"value\""
         + ",\"message\":{\"deviceId\":1,\"type\":\"cc7\""
         + ",\"parameterNumber\":" + cc + ",\"min\":0,\"max\":127}}]}";
   }

   // ── Count helpers ────────────────────────────────────────────────────

   private int countExistingTracks()
   {
      int count = 0;
      for (int i = 0; i < NUM_TRACKS; i++)
      {
         if (trackBank.getItemAt(i).exists().get()) count = i + 1;
      }
      return count;
   }

   private int countExistingDevices()
   {
      int count = 0;
      for (int i = 0; i < NUM_DEVICES; i++)
      {
         if (deviceBank.getDevice(i).exists().get()) count = i + 1;
      }
      return count;
   }

   // ── Control ID helpers ────────────────────────────────────────────────

   private int getParamControlId(int section, int paramIndex)
   {
      return (section * CONTROLS_PER_SECTION) + PARAM_OFFSET[paramIndex] + 1;
   }

   private int getNavControlId(int section, int offset)
   {
      return (section * CONTROLS_PER_SECTION) + offset + 1;
   }

   // ── Exit ──────────────────────────────────────────────────────────────

   @Override
   public void exit()
   {
      host.println("Electra One exited");
   }
}
