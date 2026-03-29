package com.electraone.bitwig;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDevice;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.RemoteControl;
import com.bitwig.extension.controller.api.Transport;

import static com.electraone.bitwig.ElectraOneMidiConfig.*;

/**
 * Electra One controller extension for Bitwig Studio (v2).
 *
 * 3 E1 touchscreen sections show 3 consecutive Remote Controls pages from
 * the selected device. The 12 physical encoders control whichever section
 * is active on the touchscreen.
 *
 * Layout per control set:
 *   Row A: [A1 Page]   [A2 P0] [A3 P1] [A4 P2] [A5 P3] [A6 Track]
 *   Row B: [B1 Device] [B2 P4] [B3 P5] [B4 P6] [B5 P7] [B6 Volume]
 *
 * Navigation:
 *   A1 — rotate through device remote-control pages
 *   A6 — select next/previous track
 *   B1 — select next/previous device
 *   B6 — rotate = track volume, touch = play/stop toggle
 */
public class ElectraOneExtension extends ControllerExtension
{
   private ControllerHost host;
   private MidiOut midiOut;
   private ElectraOneSysEx sysEx;
   private Transport transport;

   private CursorTrack cursorTrack;
   private CursorDevice cursorDevice;

   // 3 independent page cursors — one per E1 control set / section
   private CursorRemoteControlsPage[] sectionPages;

   // Which E1 control set is currently active (0-2)
   private int activeSection = 0;

   // Page navigation state
   private int basePage = 0;    // page index shown in section 0
   private int pageCount = 0;   // total remote-control pages in selected device
   private String[] cachedPageNames = {};

   // Cached navigation labels
   private String trackName = "";
   private String deviceName = "";

   // Display change detection (per section × param)
   private boolean needsFullUpdate = true;
   private final String[][] lastSentName = new String[NUM_SECTIONS][NUM_PARAMS];
   private final String[][] lastSentDisplayValue = new String[NUM_SECTIONS][NUM_PARAMS];

   // CC value feedback change detection — per section, since each set has unique CCs
   private final int[][] lastSentCC = new int[NUM_SECTIONS][NUM_PARAMS];

   // Nav display change detection
   private String lastNavTrack = "";
   private String lastNavDevice = "";
   private String lastNavVolume = "";
   private boolean lastNavPlaying = false;
   private final String[] lastNavPage = { "", "", "" };

   // Suppress CC echo for a time window after hardware input to prevent jitter.
   // The boolean flag covers the immediate flush; the timestamp extends suppression
   // so that Bitwig's value settling doesn't bounce CC back to the E1.
   private static final long ECHO_SUPPRESS_MS = 250;
   private final long[][] lastHardwareTime = new long[NUM_SECTIONS][NUM_PARAMS];

   // Navigation encoder accumulators — accumulate relative ticks, fire at threshold.
   private int thresholdPage   = 10;
   private int thresholdTrack  = 10;
   private int thresholdDevice = 10;
   private int thresholdVolume = 10;
   private int accumPage   = 0;
   private int accumTrack  = 0;
   private int accumDevice = 0;
   private int accumVolume = 0;

   // Rate limiting for navigation knobs — minimum interval between actions (ms).
   // Prevents rapid encoder turns from queuing up many navigation commands.
   private static final long NAV_RATE_LIMIT_MS = 1000;
   private long lastNavPageTime   = 0;
   private long lastNavTrackTime  = 0;
   private long lastNavDeviceTime = 0;

   // SysEx heartbeat spam filter
   private int heartbeatCount = 0;

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

      // Encoder sensitivity preferences (1 = most sensitive, 20 = slowest)
      host.getPreferences().getNumberSetting(
         "Page Encoder Sensitivity", "Encoder Speed", 1, 20, 1, "", 10)
         .addValueObserver(20, val -> thresholdPage = Math.max(1, val));

      host.getPreferences().getNumberSetting(
         "Track Encoder Sensitivity", "Encoder Speed", 1, 20, 1, "", 10)
         .addValueObserver(20, val -> thresholdTrack = Math.max(1, val));

      host.getPreferences().getNumberSetting(
         "Device Encoder Sensitivity", "Encoder Speed", 1, 20, 1, "", 10)
         .addValueObserver(20, val -> thresholdDevice = Math.max(1, val));

      host.getPreferences().getNumberSetting(
         "Volume Encoder Sensitivity", "Encoder Speed", 1, 20, 1, "", 10)
         .addValueObserver(20, val -> thresholdVolume = Math.max(1, val));

      // Transport (for play/stop toggle via B6 touch)
      transport = host.createTransport();
      transport.isPlaying().markInterested();
      transport.isPlaying().addValueObserver(playing -> host.requestFlush());

      // Cursor track
      cursorTrack = host.createCursorTrack("E1_CURSOR", "E1 Track", 0, 0, true);
      cursorTrack.name().markInterested();
      cursorTrack.name().addValueObserver(name -> {
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
      cursorDevice.name().addValueObserver(name -> {
         deviceName = name;
         basePage = 0;
         needsFullUpdate = true;
         host.requestFlush();
      });

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
            // Track total page count from section 0's page names
            sectionPages[0].pageNames().addValueObserver(names -> {
               cachedPageNames = names != null ? names : new String[0];
               pageCount = cachedPageNames.length;
               if (basePage >= pageCount && pageCount > 0)
               {
                  basePage = 0;
               }
               updateAllSectionPageIndices();
               needsFullUpdate = true;
               host.requestFlush();
            });

            // Follow Bitwig UI page selection: when the user clicks a
            // Remote Controls page in Bitwig, sync basePage so section 0
            // shows that page and sections 1-2 show the next pages.
            sectionPages[0].selectedPageIndex().addValueObserver(index -> {
               if (index >= 0 && index != basePage)
               {
                  basePage = index;
                  updateAllSectionPageIndices();
                  needsFullUpdate = true;
                  host.requestFlush();
               }
            });
         }

         // Parameter observers
         for (int p = 0; p < NUM_PARAMS; p++)
         {
            RemoteControl param = sectionPages[s].getParameter(p);
            param.markInterested();
            param.name().markInterested();
            param.displayedValue().markInterested();
            param.value().markInterested();

            // Trigger flush on any change
            param.name().addValueObserver(n -> host.requestFlush());
            param.displayedValue().addValueObserver(v -> host.requestFlush());
            param.value().addValueObserver(v -> host.requestFlush());
         }
      }

      updateAllSectionPageIndices();

      // Raw MIDI callback for CC input on port 0
      midiIn.setMidiCallback(this::onMidi);

      // SysEx callback on CTRL port for section switches and pot touch
      ctrlIn.setSysexCallback(this::onSysEx);

      // Subscribe to E1 events (section switches, pot touch)
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

      // Delayed full update (give Bitwig time to populate observers)
      host.scheduleTask(() -> {
         needsFullUpdate = true;
         host.requestFlush();
      }, 2000);

      host.println("Electra One v2.0 initialized");
   }

   // ── Page navigation ───────────────────────────────────────────────────

   private void navigatePage(int delta)
   {
      if (pageCount <= 0) return;
      basePage = ((basePage + delta) % pageCount + pageCount) % pageCount;
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
      if ((status & 0xF0) != 0xB0) return;          // CC only
      if ((status & 0x0F) != CHANNEL) return;        // our channel only

      // Navigation knobs — relative 2's complement, with accumulator.
      // Accumulate ticks until NAV_THRESHOLD is reached (~1/3 turn per step).
      int relDelta = data2 < 64 ? data2 : data2 - 128;

      long now = System.currentTimeMillis();

      switch (data1)
      {
         case CC_PAGE:
            accumPage += relDelta;
            if (accumPage >= thresholdPage && now - lastNavPageTime >= NAV_RATE_LIMIT_MS)
            {
               navigatePage(1);
               accumPage = 0;
               lastNavPageTime = now;
            }
            else if (accumPage <= -thresholdPage && now - lastNavPageTime >= NAV_RATE_LIMIT_MS)
            {
               navigatePage(-1);
               accumPage = 0;
               lastNavPageTime = now;
            }
            return;

         case CC_TRACK:
            accumTrack += relDelta;
            if (accumTrack >= thresholdTrack && now - lastNavTrackTime >= NAV_RATE_LIMIT_MS)
            {
               cursorTrack.selectNext();
               accumTrack = 0;
               lastNavTrackTime = now;
            }
            else if (accumTrack <= -thresholdTrack && now - lastNavTrackTime >= NAV_RATE_LIMIT_MS)
            {
               cursorTrack.selectPrevious();
               accumTrack = 0;
               lastNavTrackTime = now;
            }
            return;

         case CC_DEVICE:
            accumDevice += relDelta;
            if (accumDevice >= thresholdDevice && now - lastNavDeviceTime >= NAV_RATE_LIMIT_MS)
            {
               cursorDevice.selectNext();
               accumDevice = 0;
               lastNavDeviceTime = now;
            }
            else if (accumDevice <= -thresholdDevice && now - lastNavDeviceTime >= NAV_RATE_LIMIT_MS)
            {
               cursorDevice.selectPrevious();
               accumDevice = 0;
               lastNavDeviceTime = now;
            }
            return;

         case CC_VOLUME:
            accumVolume += relDelta;
            if (accumVolume >= thresholdVolume)
            {
               cursorTrack.volume().inc(1, 128);
               accumVolume = 0;
            }
            else if (accumVolume <= -thresholdVolume)
            {
               cursorTrack.volume().inc(-1, 128);
               accumVolume = 0;
            }
            return;
      }

      // Parameter knobs — check all 3 sections' CCs
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
      // Filter heartbeat spam
      if (data.contains("7e000000") || data.contains("7E000000"))
      {
         heartbeatCount++;
         return;
      }

      // Section switch (touchscreen control set change)
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

      // Pot touch — B6 touch = play/stop toggle
      int touchPotId = ElectraOneSysEx.parsePotTouch(data);
      if (touchPotId == POT_B6)
      {
         transport.togglePlay();
         return;
      }
   }

   // ── Flush (display + CC feedback) ────────────────────────────────────

   @Override
   public void flush()
   {
      boolean batch = needsFullUpdate;
      if (batch)
      {
         needsFullUpdate = false;
         sysEx.setRepaintEnabled(false);

         // Reset change caches to force full resend
         for (int s = 0; s < NUM_SECTIONS; s++)
         {
            for (int i = 0; i < NUM_PARAMS; i++)
            {
               lastSentName[s][i] = "\0";
               lastSentDisplayValue[s][i] = "\0";
               lastSentCC[s][i] = -1;
            }
         }
         lastNavTrack = "\0";
         lastNavDevice = "\0";
         lastNavVolume = "\0";
         lastNavPlaying = !transport.isPlaying().get(); // force mismatch
         for (int i = 0; i < 3; i++) lastNavPage[i] = "\0";
      }

      // Parameter display updates (all 3 sections)
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

      // Navigation display updates
      updateNavDisplay();

      // CC value feedback — send for ALL 3 sections (each has unique CCs).
      // Suppress echo for ECHO_SUPPRESS_MS after the last hardware input
      // to prevent feedback jitter between the E1 and Bitwig.
      long now = System.currentTimeMillis();
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            double val = sectionPages[s].getParameter(i).get();
            int cc7 = (int) Math.round(val * 127.0);

            if (now - lastHardwareTime[s][i] < ECHO_SUPPRESS_MS)
            {
               // Within suppression window — update cache silently
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
      String page0 = getPageName(0);
      String page1 = getPageName(1);
      String page2 = getPageName(2);

      boolean changed = !trackName.equals(lastNavTrack)
         || !deviceName.equals(lastNavDevice)
         || !volValue.equals(lastNavVolume)
         || playing != lastNavPlaying
         || !page0.equals(lastNavPage[0])
         || !page1.equals(lastNavPage[1])
         || !page2.equals(lastNavPage[2]);

      if (!changed) return;

      lastNavTrack = trackName;
      lastNavDevice = deviceName;
      lastNavVolume = volValue;
      lastNavPlaying = playing;
      lastNavPage[0] = page0;
      lastNavPage[1] = page1;
      lastNavPage[2] = page2;

      String volColor = playing ? PLAY_COLOR : NAV_COLOR;

      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         sysEx.sendControlNameColor(
            getNavControlId(s, NAV_PAGE_OFFSET), "Page", NAV_COLOR);
         sysEx.sendValueLabel(
            getNavControlId(s, NAV_PAGE_OFFSET), getPageName(s));

         sysEx.sendControlNameColor(
            getNavControlId(s, NAV_TRACK_OFFSET), "Track", NAV_COLOR);
         sysEx.sendValueLabel(
            getNavControlId(s, NAV_TRACK_OFFSET), trackName);

         sysEx.sendControlNameColor(
            getNavControlId(s, NAV_DEVICE_OFFSET), "Device", NAV_COLOR);
         sysEx.sendValueLabel(
            getNavControlId(s, NAV_DEVICE_OFFSET), deviceName);

         sysEx.sendControlNameColor(
            getNavControlId(s, NAV_VOLUME_OFFSET), "Volume", volColor);
         sysEx.sendValueLabel(
            getNavControlId(s, NAV_VOLUME_OFFSET), volValue);
      }
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

   private String getPageName(int section)
   {
      if (pageCount <= 0) return "---";
      int pageIdx = (basePage + section) % pageCount;
      if (pageIdx < cachedPageNames.length)
      {
         return cachedPageNames[pageIdx];
      }
      return "---";
   }

   // ── Exit ──────────────────────────────────────────────────────────────

   @Override
   public void exit()
   {
      sysEx.setRepaintEnabled(false);
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            int ctrlId = getParamControlId(s, i);
            sysEx.sendControlNameColor(ctrlId, "", NAV_COLOR);
            sysEx.sendValueLabel(ctrlId, "");
         }
         sysEx.sendControlNameColor(getNavControlId(s, NAV_PAGE_OFFSET), "", NAV_COLOR);
         sysEx.sendValueLabel(getNavControlId(s, NAV_PAGE_OFFSET), "");
         sysEx.sendControlNameColor(getNavControlId(s, NAV_TRACK_OFFSET), "", NAV_COLOR);
         sysEx.sendValueLabel(getNavControlId(s, NAV_TRACK_OFFSET), "");
         sysEx.sendControlNameColor(getNavControlId(s, NAV_DEVICE_OFFSET), "", NAV_COLOR);
         sysEx.sendValueLabel(getNavControlId(s, NAV_DEVICE_OFFSET), "");
         sysEx.sendControlNameColor(getNavControlId(s, NAV_VOLUME_OFFSET), "", NAV_COLOR);
         sysEx.sendValueLabel(getNavControlId(s, NAV_VOLUME_OFFSET), "");
      }
      sysEx.setRepaintEnabled(true);
      host.println("Electra One exited");
   }
}
