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
import com.bitwig.extension.controller.api.SettableEnumValue;

import static com.electraone.bitwig.ElectraOneMidiConfig.*;

/**
 * Electra One controller extension for Bitwig Studio.
 *
 * 3 control sets on the E1 touchscreen show 3 consecutive Remote Controls pages.
 * The 12 physical knobs control whichever set is active on the touchscreen.
 *
 * Per control set (2x6 grid):
 *   Row A: [A1 Track] [A2 P0] [A3 P1] [A4 P2] [A5 P3] [A6 Device]
 *   Row B: [B1 Page]  [B2 P4] [B3 P5] [B4 P6] [B5 P7] [B6 Bank]
 */
public class ElectraOneExtension extends ControllerExtension
{
   private ControllerHost host;
   private MidiOut midiOut;
   private ElectraOneSysEx sysEx;

   private CursorTrack cursorTrack;
   private CursorDevice cursorDevice;

   // 3 independent page cursors — one per E1 control set
   private CursorRemoteControlsPage[] sectionPages;

   // Which E1 control set is active (0-2), set by touchscreen SysEx
   private int activeSection = 0;

   // Base page index — section 0 shows basePage, section 1 shows basePage+1, etc.
   private int basePage = 0;

   // Dirty tracking
   private boolean needsFullUpdate = true;
   private final boolean[][] displayDirty = new boolean[NUM_SECTIONS][NUM_PARAMS];
   private final boolean[] paramValueDirty = new boolean[NUM_PARAMS];

   // Suppress CC echo when hardware originates the change
   private final boolean[] paramFromHardware = new boolean[NUM_PARAMS];

   // Cached values for change detection
   private final double[] lastSentValue = new double[NUM_PARAMS];
   private final String[][] lastSentName = new String[NUM_SECTIONS][NUM_PARAMS];
   private final String[][] lastSentDisplayValue = new String[NUM_SECTIONS][NUM_PARAMS];

   // Cached nav state
   private String cachedTrackName = "";
   private String cachedDeviceName = "";
   private String[] cachedPageNames;

   // 14-bit mode
   private boolean use14Bit = false;
   private final int[] pendingMSB = new int[NUM_PARAMS];

   // Page filter
   private boolean filterE1Pages = false;
   private int[] filteredPageIndices;

   // Suppress SysEx spam logging
   private int sysExSpamCount = 0;

   // E1 control ID offsets within a 12-control section (0-indexed)
   private static final int[] PARAM_CONTROL_OFFSET = { 1, 2, 3, 4, 7, 8, 9, 10 };

   // Nav knob offsets within a section (0-indexed)
   private static final int NAV_TRACK_OFFSET  = 0;   // A1
   private static final int NAV_DEVICE_OFFSET = 5;   // A6
   private static final int NAV_PAGE_OFFSET   = 6;   // B1
   private static final int NAV_BANK_OFFSET   = 11;  // B6

   protected ElectraOneExtension(
         ElectraOneExtensionDefinition definition, ControllerHost host)
   {
      super(definition, host);
   }

   @Override
   public void init()
   {
      host = getHost();
      host.println("=== Electra One extension starting init() ===");

      // MIDI ports
      MidiIn midiIn = host.getMidiInPort(PORT_MIDI);
      MidiIn ctrlIn = host.getMidiInPort(PORT_CTRL);
      midiOut = host.getMidiOutPort(PORT_MIDI);
      MidiOut ctrlOut = host.getMidiOutPort(PORT_CTRL);
      sysEx = new ElectraOneSysEx(ctrlOut, host);

      // Preference: 7-bit vs 14-bit
      SettableEnumValue resolutionSetting = host.getPreferences().getEnumSetting(
         "CC Resolution", "MIDI", new String[]{ "7-bit", "14-bit" }, "7-bit");
      resolutionSetting.markInterested();
      resolutionSetting.addValueObserver(val -> {
         use14Bit = "14-bit".equals(val);
         host.println("CC resolution: " + val);
         needsFullUpdate = true;
         host.requestFlush();
      });

      // Preference: page filter
      SettableEnumValue filterSetting = host.getPreferences().getEnumSetting(
         "Page Filter", "Pages", new String[]{ "All Pages", "E1 Only" }, "All Pages");
      filterSetting.markInterested();
      filterSetting.addValueObserver(val -> {
         filterE1Pages = "E1 Only".equals(val);
         host.println("Page filter: " + val);
         recomputeFilteredPages();
         basePage = 0;
         updateAllSectionPageIndices();
         needsFullUpdate = true;
         host.requestFlush();
      });

      // Cursor track + device
      cursorTrack = host.createCursorTrack("E1_CURSOR", "Cursor", 0, 0, true);
      cursorDevice = cursorTrack.createCursorDevice(
         "E1_DEVICE", "Device", 0, CursorDeviceFollowMode.FOLLOW_SELECTION);

      // Mark cursor device existence interested so API tracks device changes
      cursorDevice.exists().markInterested();
      cursorDevice.exists().addValueObserver(exists -> {
         host.println(">>> OBSERVER: device.exists = " + exists);
         needsFullUpdate = true;
         host.requestFlush();
      });

      cursorDevice.isEnabled().markInterested();
      cursorDevice.hasNext().markInterested();
      cursorDevice.hasPrevious().markInterested();

      cursorTrack.name().markInterested();
      cursorTrack.name().addValueObserver(name -> {
         host.println(">>> OBSERVER: track.name = \"" + name + "\"");
         cachedTrackName = name;
         needsFullUpdate = true;
         host.requestFlush();
      });

      cursorDevice.name().markInterested();
      cursorDevice.name().addValueObserver(name -> {
         host.println(">>> OBSERVER: device.name = \"" + name + "\"");
         cachedDeviceName = name;
         needsFullUpdate = true;
         host.requestFlush();
      });

      // 3 section pages — independent cursors on the same device
      sectionPages = new CursorRemoteControlsPage[NUM_SECTIONS];
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         sectionPages[s] = cursorDevice.createCursorRemoteControlsPage(
            "E1_SECTION_" + (s + 1), NUM_PARAMS, "");
         sectionPages[s].selectedPageIndex().markInterested();
         sectionPages[s].pageNames().markInterested();

         final int section = s;
         sectionPages[s].selectedPageIndex().addValueObserver(index -> {
            host.println(">>> OBSERVER: section[" + section + "].pageIndex = " + index);
            needsFullUpdate = true;
            host.requestFlush();
         });
         sectionPages[s].pageNames().addValueObserver(names -> {
            host.println(">>> OBSERVER: section[" + section + "].pageNames count="
               + (names != null ? names.length : 0));
            if (section == 0)
            {
               cachedPageNames = names;
               recomputeFilteredPages();
            }
            updateSectionPageIndex(section);
            needsFullUpdate = true;
            host.requestFlush();
         });

         for (int p = 0; p < NUM_PARAMS; p++)
         {
            RemoteControl param = sectionPages[s].getParameter(p);
            param.markInterested();
            param.name().markInterested();
            param.displayedValue().markInterested();

            final int sec = s;
            final int pi = p;
            param.value().addValueObserver(v -> {
               if (sec == activeSection) paramValueDirty[pi] = true;
            });
            param.name().addValueObserver(n -> {
               // Log only for section 0 to reduce spam
               if (sec == 0)
               {
                  host.println(">>> OBSERVER: section[0].param[" + pi
                     + "].name = \"" + n + "\"");
               }
               displayDirty[sec][pi] = true;
               host.requestFlush();
            });
            param.displayedValue().addValueObserver(dv -> {
               displayDirty[sec][pi] = true;
               host.requestFlush();
            });
         }
      }

      // Set initial page indices
      updateAllSectionPageIndices();

      // Raw MIDI callback for all CC input
      midiIn.setMidiCallback((int status, int data1, int data2) -> {
         if ((status & 0xF0) != 0xB0) return;
         if ((status & 0x0F) != CHANNEL) return;

         // Navigation knobs — relative 2's complement
         if (data1 == CC_TRACK)
         {
            if (data2 < 64) cursorTrack.selectNext();
            else cursorTrack.selectPrevious();
            return;
         }
         if (data1 == CC_DEVICE)
         {
            if (data2 < 64) cursorDevice.selectNext();
            else cursorDevice.selectPrevious();
            return;
         }
         if (data1 == CC_PAGE)
         {
            navigateBasePage(data2 < 64 ? 1 : -1);
            return;
         }
         if (data1 == CC_BANK)
         {
            navigateBasePage(data2 < 64 ? NUM_SECTIONS : -NUM_SECTIONS);
            return;
         }

         // Parameter knobs — direct CC-to-param mapping
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            if (data1 == PARAM_CC_MSB[i])
            {
               paramFromHardware[i] = true;
               if (use14Bit)
               {
                  pendingMSB[i] = data2;
                  return;
               }
               double normalized = data2 / 127.0;
               sectionPages[activeSection].getParameter(i).set(normalized);
               return;
            }
            if (use14Bit && data1 == PARAM_CC_LSB[i])
            {
               int combined = (pendingMSB[i] << 7) | data2;
               double normalized = combined / 16383.0;
               sectionPages[activeSection].getParameter(i).set(normalized);
               return;
            }
         }
      });

      // SysEx callback on CTRL port
      ctrlIn.setSysexCallback((String data) -> {
         // Filter out E1 heartbeat/status spam
         if (data.contains("7e000000") || data.contains("7E000000"))
         {
            sysExSpamCount++;
            if (sysExSpamCount == 1 || sysExSpamCount % 100 == 0)
            {
               host.println("SysEx heartbeat (count=" + sysExSpamCount + "): " + data);
            }
            return;
         }
         host.println("SysEx received: " + data);
         int section = ElectraOneSysEx.parseSectionSwitch(data);
         if (section >= 0 && section < NUM_SECTIONS && section != activeSection)
         {
            host.println("Section switch: " + (section + 1));
            activeSection = section;
            for (int i = 0; i < NUM_PARAMS; i++)
            {
               paramValueDirty[i] = true;
               lastSentValue[i] = -1;
            }
            needsFullUpdate = true;
            host.requestFlush();
         }
      });

      // Initialize caches
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            lastSentName[s][i] = "";
            lastSentDisplayValue[s][i] = "";
            displayDirty[s][i] = true;
         }
      }
      for (int i = 0; i < NUM_PARAMS; i++)
      {
         lastSentValue[i] = -1;
         paramValueDirty[i] = true;
      }

      // Test SysEx immediately — verify E1 display updates
      host.println("=== Sending SysEx test to control 1 and 2 ===");
      sysEx.sendControlName(1, "TEST");
      sysEx.sendValueLabel(1, "Hello");
      sysEx.sendControlUpdate(2, "MyParam", "42%");

      // Delayed initial update
      host.scheduleTask(() -> {
         host.println("=== Delayed update: device exists="
            + cursorDevice.exists().get()
            + " device=\"" + cursorDevice.name().get() + "\""
            + " track=\"" + cursorTrack.name().get() + "\"");
         needsFullUpdate = true;
         host.requestFlush();
      }, 3000);

      host.println("Electra One initialized — " + NUM_SECTIONS
         + " sections, " + NUM_PARAMS + " params each");
   }

   // ── Page navigation ─────────────────────────────────────────────────

   private void navigateBasePage(int delta)
   {
      int totalPages = getNavigablePageCount();
      if (totalPages <= 0) return;

      int newBase = basePage + delta;
      if (newBase < 0) newBase = 0;
      if (newBase >= totalPages) newBase = totalPages - 1;

      if (newBase != basePage)
      {
         basePage = newBase;
         updateAllSectionPageIndices();
         needsFullUpdate = true;
         host.requestFlush();
      }
   }

   private int getNavigablePageCount()
   {
      if (filteredPageIndices != null) return filteredPageIndices.length;
      if (cachedPageNames != null) return cachedPageNames.length;
      return 0;
   }

   private int resolvePageIndex(int section)
   {
      int logicalIndex = basePage + section;
      if (filteredPageIndices != null)
      {
         if (logicalIndex >= 0 && logicalIndex < filteredPageIndices.length)
            return filteredPageIndices[logicalIndex];
         return -1;
      }
      if (cachedPageNames != null && logicalIndex >= 0
            && logicalIndex < cachedPageNames.length)
         return logicalIndex;
      return -1;
   }

   private void updateSectionPageIndex(int section)
   {
      int actualIndex = resolvePageIndex(section);
      if (actualIndex >= 0)
      {
         sectionPages[section].selectedPageIndex().set(actualIndex);
      }
   }

   private void updateAllSectionPageIndices()
   {
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         updateSectionPageIndex(s);
      }
   }

   private void recomputeFilteredPages()
   {
      if (!filterE1Pages)
      {
         filteredPageIndices = null;
         return;
      }
      if (cachedPageNames == null)
      {
         filteredPageIndices = new int[0];
         return;
      }
      int count = 0;
      for (String name : cachedPageNames)
      {
         if (name != null && name.contains("E1")) count++;
      }
      filteredPageIndices = new int[count];
      int idx = 0;
      for (int i = 0; i < cachedPageNames.length; i++)
      {
         if (cachedPageNames[i] != null && cachedPageNames[i].contains("E1"))
         {
            filteredPageIndices[idx++] = i;
         }
      }
   }

   // ── Flush cycle ─────────────────────────────────────────────────────

   @Override
   public void flush()
   {
      // Stage 1: Full display refresh
      if (needsFullUpdate)
      {
         needsFullUpdate = false;
         sendFullDisplayUpdate();
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            paramValueDirty[i] = true;
         }
      }

      // Stage 2: Incremental display updates for ALL sections
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            if (displayDirty[s][i])
            {
               displayDirty[s][i] = false;
               RemoteControl param = sectionPages[s].getParameter(i);
               String name = param.name().get();
               String displayValue = param.displayedValue().get();

               if (!name.equals(lastSentName[s][i])
                     || !displayValue.equals(lastSentDisplayValue[s][i]))
               {
                  lastSentName[s][i] = name;
                  lastSentDisplayValue[s][i] = displayValue;
                  int controlId = getParamControlId(s, i);
                  host.println("  incremental S" + s + " P" + i
                     + " id=" + controlId + " name=\"" + name
                     + "\" val=\"" + displayValue + "\"");
                  sysEx.sendControlUpdate(controlId, name, displayValue);
               }
            }
         }
      }

      // Stage 3: CC value feedback for active section
      for (int i = 0; i < NUM_PARAMS; i++)
      {
         if (paramFromHardware[i])
         {
            paramFromHardware[i] = false;
            paramValueDirty[i] = false;
            lastSentValue[i] = sectionPages[activeSection].getParameter(i).get();
            continue;
         }
         if (paramValueDirty[i])
         {
            paramValueDirty[i] = false;
            double value = sectionPages[activeSection].getParameter(i).get();

            if (value != lastSentValue[i])
            {
               lastSentValue[i] = value;
               sendParamCC(i, value);
            }
         }
      }
   }

   private void sendFullDisplayUpdate()
   {
      host.println("sendFullDisplayUpdate: track=\"" + cachedTrackName
         + "\" device=\"" + cachedDeviceName + "\" basePage=" + basePage
         + " deviceExists=" + cursorDevice.exists().get());

      // Disable repaint during batch update for performance
      sysEx.setRepaintEnabled(false);

      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            RemoteControl param = sectionPages[s].getParameter(i);
            String name = param.name().get();
            String displayValue = param.displayedValue().get();
            int controlId = getParamControlId(s, i);
            if (s == 0)
            {
               host.println("  S0 P" + i + " id=" + controlId
                  + " name=\"" + name + "\" val=\"" + displayValue + "\"");
            }
            sysEx.sendControlUpdate(controlId, name, displayValue);
            lastSentName[s][i] = name;
            lastSentDisplayValue[s][i] = displayValue;
         }

         int trackId = getNavControlId(s, NAV_TRACK_OFFSET);
         int deviceId = getNavControlId(s, NAV_DEVICE_OFFSET);
         int pageId = getNavControlId(s, NAV_PAGE_OFFSET);
         int bankId = getNavControlId(s, NAV_BANK_OFFSET);

         sysEx.sendControlUpdate(trackId, "Track", cachedTrackName);
         sysEx.sendControlUpdate(deviceId, "Device", cachedDeviceName);
         sysEx.sendControlUpdate(pageId, "Page", getPageName(s));

         int pageCount = getNavigablePageCount();
         int pageNum = basePage + s + 1;
         String bankLabel = pageCount > 0
            ? pageNum + "/" + pageCount
            : "---";
         sysEx.sendControlUpdate(bankId, "Bank", bankLabel);
      }

      // Re-enable repaint to trigger a single screen refresh
      sysEx.setRepaintEnabled(true);
   }

   // ── Control ID mapping ──────────────────────────────────────────────

   private int getParamControlId(int section, int paramIndex)
   {
      return (section * CONTROLS_PER_SECTION) + PARAM_CONTROL_OFFSET[paramIndex] + 1;
   }

   private int getNavControlId(int section, int offset)
   {
      return (section * CONTROLS_PER_SECTION) + offset + 1;
   }

   private String getPageName(int section)
   {
      int actualIndex = resolvePageIndex(section);
      if (actualIndex >= 0 && cachedPageNames != null
            && actualIndex < cachedPageNames.length)
      {
         return cachedPageNames[actualIndex];
      }
      return "---";
   }

   // ── CC output ───────────────────────────────────────────────────────

   private void sendParamCC(int paramIndex, double value)
   {
      if (use14Bit)
      {
         int val14 = (int) Math.round(value * 16383.0);
         int msb = (val14 >> 7) & 0x7F;
         int lsb = val14 & 0x7F;
         midiOut.sendMidi(0xB0 | CHANNEL, PARAM_CC_MSB[paramIndex], msb);
         midiOut.sendMidi(0xB0 | CHANNEL, PARAM_CC_LSB[paramIndex], lsb);
      }
      else
      {
         int val7 = (int) Math.round(value * 127.0);
         midiOut.sendMidi(0xB0 | CHANNEL, PARAM_CC_MSB[paramIndex], val7);
      }
   }

   @Override
   public void exit()
   {
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            sysEx.sendControlUpdate(getParamControlId(s, i), "", "");
         }
         sysEx.sendControlUpdate(getNavControlId(s, NAV_TRACK_OFFSET), "", "");
         sysEx.sendControlUpdate(getNavControlId(s, NAV_DEVICE_OFFSET), "", "");
         sysEx.sendControlUpdate(getNavControlId(s, NAV_PAGE_OFFSET), "", "");
         sysEx.sendControlUpdate(getNavControlId(s, NAV_BANK_OFFSET), "", "");
      }
      host.println("Electra One exited");
   }
}
