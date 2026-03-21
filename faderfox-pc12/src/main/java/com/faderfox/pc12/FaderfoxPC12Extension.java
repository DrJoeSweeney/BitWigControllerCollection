package com.faderfox.pc12;

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
import com.bitwig.extension.controller.api.RemoteControl;

public class FaderfoxPC12Extension extends ControllerExtension
{
   private static final int FIRST_CC = 10;
   private static final int NUM_KNOBS_PER_GROUP = 8;
   private static final int NUM_GROUPS = 9;
   private static final int NUM_CHANNELS = 16;
   private static final int TOTAL_KNOBS = NUM_GROUPS * NUM_KNOBS_PER_GROUP; // 72

   private AbsoluteHardwareKnob[][] knobs; // [channel][knob]
   private CursorRemoteControlsPage[] remoteControls;
   private HardwareBinding[][] groupBindings; // [group][binding]
   private ControllerHost host;

   protected FaderfoxPC12Extension(
         FaderfoxPC12ExtensionDefinition definition, ControllerHost host)
   {
      super(definition, host);
   }

   @Override
   public void init()
   {
      host = getHost();
      host.println("=== FaderFox PC12 extension starting init() ===");
      host.println("API version: " + host.getHostApiVersion());
      host.println("Host version: " + host.getHostVersion());
      host.println("Loaded at: " + java.time.LocalTime.now());

      final MidiIn midiIn = host.getMidiInPort(0);
      host.println("MIDI In port acquired");

      // Device cursor
      CursorTrack cursorTrack = host.createCursorTrack("PC12_CURSOR", "Cursor", 0, 0, true);
      CursorDevice cursorDevice = cursorTrack.createCursorDevice(
         "PC12_DEVICE", "Device", 0, CursorDeviceFollowMode.FOLLOW_SELECTION);

      // One CursorRemoteControlsPage per group so all can be active simultaneously
      remoteControls = new CursorRemoteControlsPage[NUM_GROUPS];
      for (int g = 0; g < NUM_GROUPS; g++)
      {
         remoteControls[g] = cursorDevice.createCursorRemoteControlsPage(
            "FF_GROUP_" + (g + 1), NUM_KNOBS_PER_GROUP, "");
         remoteControls[g].pageNames().markInterested();
         remoteControls[g].selectedPageIndex().markInterested();

         for (int i = 0; i < NUM_KNOBS_PER_GROUP; i++)
         {
            RemoteControl param = remoteControls[g].getParameter(i);
            param.markInterested();
            param.name().markInterested();
         }
      }

      // Hardware surface
      HardwareSurface surface = host.createHardwareSurface();

      // Create 72 knobs × 16 channels
      knobs = new AbsoluteHardwareKnob[NUM_CHANNELS][TOTAL_KNOBS];
      for (int ch = 0; ch < NUM_CHANNELS; ch++)
      {
         for (int i = 0; i < TOTAL_KNOBS; i++)
         {
            int cc = FIRST_CC + i;
            knobs[ch][i] = surface.createAbsoluteHardwareKnob("KNOB_CH" + ch + "_CC" + cc);
            knobs[ch][i].setAdjustValueMatcher(midiIn.createAbsoluteCCValueMatcher(ch, cc));
         }
      }

      // Initialize per-group binding tracking
      groupBindings = new HardwareBinding[NUM_GROUPS][];
      for (int g = 0; g < NUM_GROUPS; g++)
      {
         groupBindings[g] = new HardwareBinding[0];
      }

      // Bind/unbind each group's knobs when page names change
      for (int g = 0; g < NUM_GROUPS; g++)
      {
         final int groupIndex = g;
         final String prefix = "FF" + (g + 1);
         remoteControls[g].pageNames().addValueObserver(names -> {
            updateGroupBindings(groupIndex, prefix, names);
         });
      }

      host.println("Faderfox PC12 initialized — " + NUM_GROUPS + " groups bound");
   }

   private void updateGroupBindings(int groupIndex, String prefix, String[] names)
   {
      // Remove existing bindings for this group
      for (HardwareBinding binding : groupBindings[groupIndex])
      {
         binding.removeBinding();
      }
      groupBindings[groupIndex] = new HardwareBinding[0];

      // Search for matching FFn page
      for (int i = 0; i < names.length; i++)
      {
         if (names[i] != null && names[i].startsWith(prefix)
            && (names[i].length() == prefix.length()
                || names[i].charAt(prefix.length()) == ' '))
         {
            remoteControls[groupIndex].selectedPageIndex().set(i);

            // Bind all 16 channels of this group's knobs to the cursor's parameters
            HardwareBinding[] bindings = new HardwareBinding[NUM_CHANNELS * NUM_KNOBS_PER_GROUP];
            int b = 0;
            for (int ch = 0; ch < NUM_CHANNELS; ch++)
            {
               for (int p = 0; p < NUM_KNOBS_PER_GROUP; p++)
               {
                  int knobIndex = groupIndex * NUM_KNOBS_PER_GROUP + p;
                  bindings[b++] = knobs[ch][knobIndex].addBinding(
                     remoteControls[groupIndex].getParameter(p));
               }
            }
            groupBindings[groupIndex] = bindings;

            host.println("Group " + (groupIndex + 1) + " → page " + i + " (" + names[i] + ")");
            return;
         }
      }
      host.println("Group " + (groupIndex + 1) + " → no matching page, unbound");
   }

   @Override
   public void flush()
   {
      // No MIDI out — nothing to send
   }

   @Override
   public void exit()
   {
      getHost().println("Faderfox PC12 exited");
   }
}
