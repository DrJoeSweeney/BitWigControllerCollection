package com.electraone.bitwig;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.AbsoluteHardwareKnob;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDevice;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.HardwareBinding;
import com.bitwig.extension.controller.api.HardwareSurface;
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
 * Swiping between sets on the E1 triggers a section switch via SysEx.
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

   private AbsoluteHardwareKnob[] paramKnobs;
   private HardwareBinding[] paramBindings;

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

   // Page filter — when enabled, only pages containing "E1" are navigable
   private boolean filterE1Pages = false;
   private int[] filteredPageIndices;

   // E1 control ID offsets within a 12-control section (0-indexed)
   // A2-A5 = offsets 1-4, B2-B5 = offsets 7-10
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
      sysEx = new ElectraOneSysEx(ctrlOut);

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

      cursorTrack.name().markInterested();
      cursorTrack.name().addValueObserver(name -> {
         cachedTrackName = name;
         needsFullUpdate = true;
         host.requestFlush();
      });

      cursorDevice.name().markInterested();
      cursorDevice.name().addValueObserver(name -> {
         cachedDeviceName = name;
         needsFullUpdate = true;
         host.requestFlush();
      });

      // 3 section pages — independent cursors on the same device, showing consecutive pages
      sectionPages = new CursorRemoteControlsPage[NUM_SECTIONS];
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         sectionPages[s] = cursorDevice.createCursorRemoteControlsPage(
            "E1_SECTION_" + (s + 1), NUM_PARAMS, "");
         sectionPages[s].selectedPageIndex().markInterested();
         sectionPages[s].pageNames().markInterested();

         final int section = s;
         sectionPages[s].selectedPageIndex().addValueObserver(index -> {
            needsFullUpdate = true;
            host.requestFlush();
         });
         sectionPages[s].pageNames().addValueObserver(names -> {
            // All sections share the same page list; use section 0 as canonical
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
               displayDirty[sec][pi] = true;
            });
            param.displayedValue().addValueObserver(dv -> {
               displayDirty[sec][pi] = true;
            });
         }
      }

      // Set initial page indices: section 0 = page 0, section 1 = page 1, section 2 = page 2
      updateAllSectionPageIndices();

      // Hardware surface — parameter knobs (absolute CC)
      HardwareSurface surface = host.createHardwareSurface();
      paramKnobs = new AbsoluteHardwareKnob[NUM_PARAMS];
      paramBindings = new HardwareBinding[NUM_PARAMS];

      for (int i = 0; i < NUM_PARAMS; i++)
      {
         paramKnobs[i] = surface.createAbsoluteHardwareKnob("PARAM_" + i);
         paramKnobs[i].setAdjustValueMatcher(
            midiIn.createAbsoluteCCValueMatcher(CHANNEL, PARAM_CC_MSB[i]));
      }
      bindParamKnobs();

      // Raw MIDI callback for navigation knobs + 14-bit LSB
      midiIn.setMidiCallback((int status, int data1, int data2) -> {
         if ((status & 0xF0) != 0xB0) return;
         if ((status & 0x0F) != CHANNEL) return;

         // Navigation knobs — relative 2's complement
         // 1-63 = clockwise (forward), 65-127 = counter-clockwise (backward)
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
            // Shift base page by 1 — all 3 sections move together
            navigateBasePage(data2 < 64 ? 1 : -1);
            return;
         }
         if (data1 == CC_BANK)
         {
            // Jump by NUM_SECTIONS pages (one full screen's worth)
            navigateBasePage(data2 < 64 ? NUM_SECTIONS : -NUM_SECTIONS);
            return;
         }

         // Parameter CC — mark hardware-originated to suppress echo in flush
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

      // SysEx callback on CTRL port for E1 touchscreen section switching
      ctrlIn.setSysexCallback((String data) -> {
         int section = ElectraOneSysEx.parseSectionSwitch(data);
         if (section >= 0 && section < NUM_SECTIONS && section != activeSection)
         {
            host.println("Section switch: " + (section + 1));
            activeSection = section;
            rebindParamKnobs();
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

      host.println("Electra One initialized — " + NUM_SECTIONS
         + " sections, " + NUM_PARAMS + " params each");
   }

   // ── Param knob binding ──────────────────────────────────────────────

   private void bindParamKnobs()
   {
      for (int i = 0; i < NUM_PARAMS; i++)
      {
         paramBindings[i] = paramKnobs[i].addBinding(
            sectionPages[activeSection].getParameter(i));
      }
   }

   private void rebindParamKnobs()
   {
      for (int i = 0; i < NUM_PARAMS; i++)
      {
         if (paramBindings[i] != null)
         {
            paramBindings[i].removeBinding();
            paramBindings[i] = null;
         }
      }
      bindParamKnobs();

      // Mark all params dirty so flush sends fresh CC values
      for (int i = 0; i < NUM_PARAMS; i++)
      {
         paramValueDirty[i] = true;
         lastSentValue[i] = -1;
      }
   }

   // ── Page navigation ─────────────────────────────────────────────────

   /**
    * Shift the base page by delta, respecting page filter and bounds.
    * All 3 sections move together.
    */
   private void navigateBasePage(int delta)
   {
      int totalPages = getNavigablePageCount();
      if (totalPages <= 0) return;

      int newBase = basePage + delta;
      // Clamp: base must be >= 0 and section 0 must have a valid page
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

   /**
    * Resolve the actual page index for a section.
    * @return actual page index, or -1 if out of range
    */
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
      // Stage 1: Full display refresh (track/device/page changed)
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
                  sysEx.sendControlUpdate(
                     getParamControlId(s, i), name, displayValue);
               }
            }
         }
      }

      // Stage 3: CC value feedback for active section (suppress hardware echo)
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

   /**
    * Send full display update: all 3 sections x 8 params + nav labels.
    */
   private void sendFullDisplayUpdate()
   {
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         // Parameter names and values
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            RemoteControl param = sectionPages[s].getParameter(i);
            String name = param.name().get();
            String displayValue = param.displayedValue().get();
            sysEx.sendControlUpdate(getParamControlId(s, i), name, displayValue);
            lastSentName[s][i] = name;
            lastSentDisplayValue[s][i] = displayValue;
         }

         // Nav labels per section
         sysEx.sendControlUpdate(getNavControlId(s, NAV_TRACK_OFFSET),
            "Track", cachedTrackName);
         sysEx.sendControlUpdate(getNavControlId(s, NAV_DEVICE_OFFSET),
            "Device", cachedDeviceName);
         sysEx.sendControlUpdate(getNavControlId(s, NAV_PAGE_OFFSET),
            "Page", getPageName(s));

         int pageCount = getNavigablePageCount();
         int pageNum = basePage + s + 1;
         String bankLabel = pageCount > 0
            ? pageNum + "/" + pageCount
            : "---";
         sysEx.sendControlUpdate(getNavControlId(s, NAV_BANK_OFFSET),
            "Bank", bankLabel);
      }
   }

   // ── Control ID mapping ──────────────────────────────────────────────

   /**
    * E1 preset control ID for a parameter knob.
    * Section 0: IDs 1-12, Section 1: 13-24, Section 2: 25-36.
    * Param knobs are at offsets 1,2,3,4 (A2-A5) and 7,8,9,10 (B2-B5).
    */
   private int getParamControlId(int section, int paramIndex)
   {
      return (section * CONTROLS_PER_SECTION) + PARAM_CONTROL_OFFSET[paramIndex] + 1;
   }

   /**
    * E1 preset control ID for a navigation knob.
    * @param section the section (0-2)
    * @param offset the knob offset within the section (0, 5, 6, or 11)
    */
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
