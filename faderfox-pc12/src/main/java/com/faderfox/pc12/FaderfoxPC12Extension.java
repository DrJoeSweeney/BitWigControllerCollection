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
   private HardwareBinding[] activeBindings;
   private CursorRemoteControlsPage remoteControls;
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
      final MidiIn midiIn = host.getMidiInPort(0);

      // Device cursor
      CursorTrack cursorTrack = host.createCursorTrack("PC12_CURSOR", "Cursor", 0, 0, true);
      CursorDevice cursorDevice = cursorTrack.createCursorDevice(
         "PC12_DEVICE", "Device", 0, CursorDeviceFollowMode.FOLLOW_SELECTION);

      remoteControls = cursorDevice.createCursorRemoteControlsPage(NUM_KNOBS_PER_GROUP);
      remoteControls.pageNames().markInterested();
      remoteControls.selectedPageIndex().markInterested();

      for (int i = 0; i < NUM_KNOBS_PER_GROUP; i++)
      {
         RemoteControl param = remoteControls.getParameter(i);
         param.markInterested();
         param.name().markInterested();
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

      activeBindings = new HardwareBinding[0];

      // Observe page changes
      remoteControls.pageNames().addValueObserver(names -> updateActiveBindings());
      remoteControls.selectedPageIndex().addValueObserver(idx -> updateActiveBindings());

      host.println("Faderfox PC12 initialized");
   }

   private void updateActiveBindings()
   {
      // Remove existing bindings
      for (HardwareBinding binding : activeBindings)
      {
         binding.removeBinding();
      }

      int pageIndex = remoteControls.selectedPageIndex().get();
      if (pageIndex < 0)
      {
         activeBindings = new HardwareBinding[0];
         return;
      }

      String[] names = remoteControls.pageNames().get();
      if (pageIndex >= names.length)
      {
         activeBindings = new HardwareBinding[0];
         return;
      }

      String pageName = names[pageIndex];
      PageConfig config = parsePageName(pageName);
      if (config == null)
      {
         activeBindings = new HardwareBinding[0];
         return;
      }

      if (config.channel == -1)
      {
         // Any channel — bind all 16 channels for this group
         HardwareBinding[] bindings = new HardwareBinding[NUM_CHANNELS * NUM_KNOBS_PER_GROUP];
         int idx = 0;
         for (int ch = 0; ch < NUM_CHANNELS; ch++)
         {
            for (int p = 0; p < NUM_KNOBS_PER_GROUP; p++)
            {
               bindings[idx++] = knobs[ch][config.groupIndex * NUM_KNOBS_PER_GROUP + p]
                  .addBinding(remoteControls.getParameter(p));
            }
         }
         activeBindings = bindings;
      }
      else
      {
         // Specific channel only
         HardwareBinding[] bindings = new HardwareBinding[NUM_KNOBS_PER_GROUP];
         for (int p = 0; p < NUM_KNOBS_PER_GROUP; p++)
         {
            bindings[p] = knobs[config.channel][config.groupIndex * NUM_KNOBS_PER_GROUP + p]
               .addBinding(remoteControls.getParameter(p));
         }
         activeBindings = bindings;
      }
   }

   private static PageConfig parsePageName(String name)
   {
      if (name == null || name.length() < 3 || !name.startsWith("FF")) return null;

      // Check for "FF<1-9>" optionally followed by " CH<0-15>"
      char groupChar = name.charAt(2);
      if (groupChar < '1' || groupChar > '9') return null;
      int groupIndex = groupChar - '1';

      if (name.length() == 3)
      {
         // "FFn" — any channel
         return new PageConfig(groupIndex, -1);
      }

      if (name.length() >= 7 && name.startsWith(" CH", 3))
      {
         String chStr = name.substring(6);
         try
         {
            int ch = Integer.parseInt(chStr);
            if (ch >= 0 && ch <= 15) return new PageConfig(groupIndex, ch);
         }
         catch (NumberFormatException e)
         {
            return null;
         }
      }

      return null;
   }

   private static class PageConfig
   {
      final int groupIndex;
      final int channel; // -1 means all channels

      PageConfig(int groupIndex, int channel)
      {
         this.groupIndex = groupIndex;
         this.channel = channel;
      }
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
