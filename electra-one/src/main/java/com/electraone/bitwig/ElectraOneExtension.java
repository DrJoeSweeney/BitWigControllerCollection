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

public class ElectraOneExtension extends ControllerExtension
{
   private ControllerHost host;
   private MidiOut midiOut;
   private MidiOut ctrlOut;
   private ElectraOneSysEx sysEx;

   private CursorTrack cursorTrack;
   private CursorDevice cursorDevice;
   private CursorRemoteControlsPage[] sectionPages;

   private AbsoluteHardwareKnob[] paramKnobs;
   private HardwareBinding[] paramBindings;

   private int activeSection = 0;
   private int basePage = 0;

   // Dirty tracking — per-section for display, active-section-only for CC feedback
   private boolean needsFullUpdate = true;
   private final boolean[][] sectionDisplayDirty = new boolean[NUM_SECTIONS][NUM_PARAMS];
   private final boolean[] paramValueDirty = new boolean[NUM_PARAMS];

   // Suppress CC echo: set when E1 hardware sends CC, cleared in flush
   private final boolean[] paramFromHardware = new boolean[NUM_PARAMS];

   // Cached values for change detection
   private final double[] lastSentValue = new double[NUM_PARAMS];
   private final String[][] lastSentName = new String[NUM_SECTIONS][NUM_PARAMS];
   private final String[][] lastSentDisplayValue = new String[NUM_SECTIONS][NUM_PARAMS];

   // Cached state for display
   private String cachedTrackName = "";
   private String cachedDeviceName = "";
   private final String[][] cachedPageNames = new String[NUM_SECTIONS][];

   // 14-bit mode
   private boolean use14Bit = false;
   private final int[] pendingMSB = new int[NUM_PARAMS];

   // Page filter — when enabled, only pages containing "E1" are navigable
   private boolean filterE1Pages = false;
   private int[] filteredPageIndices;  // actual page indices that pass filter; null = no filter

   // E1 preset control IDs — 12 controls per section, params are indices 1-4 and 7-10
   // Section 1: controls 1-12, Section 2: 13-24, Section 3: 25-36
   // Within each section: knob positions map to control offsets 0-11
   // Inner param knobs are at offsets 1,2,3,4 (row1) and 7,8,9,10 (row2) — 0-indexed within section
   private static final int[] PARAM_CONTROL_OFFSET = { 1, 2, 3, 4, 7, 8, 9, 10 };

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
      ctrlOut = host.getMidiOutPort(PORT_CTRL);
      sysEx = new ElectraOneSysEx(ctrlOut);

      // Preference: 7-bit vs 14-bit
      SettableEnumValue resolutionSetting = host.getPreferences().getEnumSetting(
         "CC Resolution", "MIDI", new String[]{ "7-bit", "14-bit" }, "7-bit");
      resolutionSetting.markInterested();
      resolutionSetting.addValueObserver(val -> {
         use14Bit = "14-bit".equals(val);
         host.println("CC resolution: " + val);
         needsFullUpdate = true;
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
      });

      cursorDevice.name().markInterested();
      cursorDevice.name().addValueObserver(name -> {
         cachedDeviceName = name;
         needsFullUpdate = true;
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
            needsFullUpdate = true;
         });
         sectionPages[s].pageNames().addValueObserver(names -> {
            cachedPageNames[section] = names;
            // Recompute filter when section 0 reports (all sections share same page list)
            if (section == 0) recomputeFilteredPages();
            // Set this section's page to its expected index
            updateSectionPageIndex(section);
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
               sectionDisplayDirty[sec][pi] = true;
            });
            param.displayedValue().addValueObserver(dv -> {
               sectionDisplayDirty[sec][pi] = true;
            });
         }
      }

      // Set initial section page indices
      updateAllSectionPageIndices();

      // Hardware surface
      HardwareSurface surface = host.createHardwareSurface();

      // Parameter knobs (absolute, 7-bit by default)
      paramKnobs = new AbsoluteHardwareKnob[NUM_PARAMS];
      paramBindings = new HardwareBinding[NUM_PARAMS];
      for (int i = 0; i < NUM_PARAMS; i++)
      {
         paramKnobs[i] = surface.createAbsoluteHardwareKnob("PARAM_" + i);
         paramKnobs[i].setAdjustValueMatcher(
            midiIn.createAbsoluteCCValueMatcher(CHANNEL, PARAM_CC_MSB[i]));
      }
      bindParamKnobs();

      // Raw MIDI callback for navigation knobs + 14-bit LSB handling
      midiIn.setMidiCallback((int status, int data1, int data2) -> {
         // Only handle CC messages on our channel
         if ((status & 0xF0) != 0xB0) return;
         if ((status & 0x0F) != CHANNEL) return;

         // Navigation knobs — relative 2's complement
         // Values 1-63 = clockwise (increment), 65-127 = counter-clockwise (decrement)
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
            if (filteredPageIndices != null)
            {
               // Navigate to next/prev filtered page for active section
               int currentActual = sectionPages[activeSection].selectedPageIndex().get();
               if (data2 < 64)
               {
                  for (int fi : filteredPageIndices)
                  {
                     if (fi > currentActual)
                     {
                        sectionPages[activeSection].selectedPageIndex().set(fi);
                        break;
                     }
                  }
               }
               else
               {
                  for (int j = filteredPageIndices.length - 1; j >= 0; j--)
                  {
                     if (filteredPageIndices[j] < currentActual)
                     {
                        sectionPages[activeSection].selectedPageIndex().set(
                           filteredPageIndices[j]);
                        break;
                     }
                  }
               }
            }
            else
            {
               if (data2 < 64) sectionPages[activeSection].selectNextPage(false);
               else sectionPages[activeSection].selectPreviousPage(false);
            }
            return;
         }
         if (data1 == CC_BANK)
         {
            int maxBase = filteredPageIndices != null
               ? filteredPageIndices.length : Integer.MAX_VALUE;
            if (data2 < 64)
            {
               if (basePage + NUM_SECTIONS < maxBase)
               {
                  basePage += NUM_SECTIONS;
                  updateAllSectionPageIndices();
                  needsFullUpdate = true;
                  host.requestFlush();
               }
            }
            else if (basePage >= NUM_SECTIONS)
            {
               basePage -= NUM_SECTIONS;
               updateAllSectionPageIndices();
               needsFullUpdate = true;
               host.requestFlush();
            }
            return;
         }

         // Parameter CC handling — mark as hardware-originated to suppress echo
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            if (data1 == PARAM_CC_MSB[i])
            {
               paramFromHardware[i] = true;
               if (use14Bit)
               {
                  pendingMSB[i] = data2;
                  return; // Wait for LSB
               }
               return; // 7-bit: hardware surface binding handles value
            }
            if (use14Bit && data1 == PARAM_CC_LSB[i])
            {
               // Combine with last MSB to form 14-bit value
               int combined = (pendingMSB[i] << 7) | data2;
               double normalized = combined / 16383.0;
               sectionPages[activeSection].getParameter(i).set(normalized);
               return;
            }
         }
      });

      // SysEx callback on CTRL port for section switching
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
            sectionDisplayDirty[s][i] = true;
         }
      }
      for (int i = 0; i < NUM_PARAMS; i++)
      {
         lastSentValue[i] = -1;
         paramValueDirty[i] = true;
      }

      host.println("Electra One initialized — 3 sections, " + NUM_PARAMS + " params per section");
   }

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
      // Remove old bindings
      for (int i = 0; i < NUM_PARAMS; i++)
      {
         if (paramBindings[i] != null)
         {
            paramBindings[i].removeBinding();
            paramBindings[i] = null;
         }
      }
      // Create new bindings for active section
      bindParamKnobs();

      // Mark all params dirty so flush sends fresh CC values
      for (int i = 0; i < NUM_PARAMS; i++)
      {
         paramValueDirty[i] = true;
         lastSentValue[i] = -1;
      }
   }

   /**
    * Resolve the actual page index for a section, respecting the page filter.
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
      return logicalIndex;
   }

   private void updateSectionPageIndex(int section)
   {
      int actualIndex = resolvePageIndex(section);
      if (actualIndex < 0) return;
      String[] names = cachedPageNames[section];
      if (names != null && actualIndex < names.length)
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

   /**
    * Rebuild the filtered page index list from section 0's cached page names.
    * When filter is off, sets filteredPageIndices to null (no filtering).
    */
   private void recomputeFilteredPages()
   {
      if (!filterE1Pages)
      {
         filteredPageIndices = null;
         return;
      }
      String[] names = cachedPageNames[0];
      if (names == null)
      {
         filteredPageIndices = new int[0];
         return;
      }
      int count = 0;
      for (String name : names)
      {
         if (name != null && name.contains("E1")) count++;
      }
      filteredPageIndices = new int[count];
      int idx = 0;
      for (int i = 0; i < names.length; i++)
      {
         if (names[i] != null && names[i].contains("E1"))
         {
            filteredPageIndices[idx++] = i;
         }
      }
   }

   @Override
   public void flush()
   {
      // Stage 1: Full display refresh
      if (needsFullUpdate)
      {
         needsFullUpdate = false;
         sendFullDisplayUpdate();

         // Mark all active section params dirty for CC feedback
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
            if (sectionDisplayDirty[s][i])
            {
               sectionDisplayDirty[s][i] = false;
               RemoteControl param = sectionPages[s].getParameter(i);
               String name = param.name().get();
               String displayValue = param.displayedValue().get();

               if (!name.equals(lastSentName[s][i])
                     || !displayValue.equals(lastSentDisplayValue[s][i]))
               {
                  lastSentName[s][i] = name;
                  lastSentDisplayValue[s][i] = displayValue;
                  int controlId = getControlId(s, i);
                  sysEx.sendControlUpdate(controlId, name, displayValue);
               }
            }
         }
      }

      // Stage 3: CC value feedback for active section parameters
      // Skip feedback for params that were just changed by hardware input (suppress echo)
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
      // Send parameter names and values for all 3 sections
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            RemoteControl param = sectionPages[s].getParameter(i);
            String name = param.name().get();
            String displayValue = param.displayedValue().get();
            int controlId = getControlId(s, i);

            sysEx.sendControlUpdate(controlId, name, displayValue);

            // Update cache
            lastSentName[s][i] = name;
            lastSentDisplayValue[s][i] = displayValue;
         }
      }

      // Send track name to navigation controls (knob 1 area)
      // Using control IDs for nav knobs: offset 0 (track), 5 (device), 6 (page), 11 (bank)
      sysEx.sendControlUpdate(getNavControlId(activeSection, 0),
         "Track", cachedTrackName);
      sysEx.sendControlUpdate(getNavControlId(activeSection, 5),
         "Device", cachedDeviceName);

      // Send page names
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         String pageName = getPageName(s);
         sysEx.sendControlUpdate(getNavControlId(s, 6),
            "Page", pageName);
      }

      sysEx.sendControlUpdate(getNavControlId(activeSection, 11),
         "Bank", "Bank " + ((basePage / NUM_SECTIONS) + 1));
   }

   /**
    * Get the E1 preset control ID for a parameter knob.
    * Section 0: controls 1-12, Section 1: 13-24, Section 2: 25-36
    * Param knobs are at offsets 1,2,3,4,7,8,9,10 within each section.
    */
   private int getControlId(int section, int paramIndex)
   {
      return (section * 12) + PARAM_CONTROL_OFFSET[paramIndex] + 1;
   }

   /**
    * Get the E1 preset control ID for a navigation knob.
    * @param section the section (0-2)
    * @param offset the knob offset within the section (0, 5, 6, or 11)
    */
   private int getNavControlId(int section, int offset)
   {
      return (section * 12) + offset + 1;
   }

   private String getPageName(int section)
   {
      int actualIndex = resolvePageIndex(section);
      if (actualIndex < 0) return "---";
      String[] names = cachedPageNames[section];
      if (names != null && actualIndex < names.length)
      {
         return names[actualIndex];
      }
      return "---";
   }

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
      // Clear the E1 display by sending empty names/values
      for (int s = 0; s < NUM_SECTIONS; s++)
      {
         for (int i = 0; i < NUM_PARAMS; i++)
         {
            int controlId = getControlId(s, i);
            sysEx.sendControlUpdate(controlId, "", "");
         }
      }
      host.println("Electra One exited");
   }
}
