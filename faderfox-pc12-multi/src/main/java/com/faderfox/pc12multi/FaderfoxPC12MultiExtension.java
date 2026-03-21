package com.faderfox.pc12multi;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.AbsoluteHardwareKnob;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDevice;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.HardwareBinding;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.RemoteControl;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;

public class FaderfoxPC12MultiExtension extends ControllerExtension
{
   private static final int FIRST_CC = 10;
   private static final int NUM_KNOBS_PER_GROUP = 8;
   private static final int NUM_GROUPS = 9;
   private static final int NUM_CHANNELS = 16;
   private static final int NUM_TRACKS = 16;
   private static final int TOTAL_KNOBS = NUM_GROUPS * NUM_KNOBS_PER_GROUP; // 72

   private AbsoluteHardwareKnob[][] knobs;                // [channel][knobIndex]
   private CursorDevice[] cursorDevices;                   // [track]
   private CursorRemoteControlsPage[][] remoteControls;    // [track][group]
   private HardwareBinding[][][] groupBindings;            // [track][group][binding]
   private ControllerHost host;

   protected FaderfoxPC12MultiExtension(
         FaderfoxPC12MultiExtensionDefinition definition, ControllerHost host)
   {
      super(definition, host);
   }

   @Override
   public void init()
   {
      host = getHost();
      host.println("=== FaderFox PC12 MultiInstrument starting init() ===");

      final MidiIn midiIn = host.getMidiInPort(0);

      // Track bank covering first 16 tracks
      TrackBank trackBank = host.createTrackBank(NUM_TRACKS, 0, 0);

      // Per-track device cursors and remote control pages
      cursorDevices = new CursorDevice[NUM_TRACKS];
      remoteControls = new CursorRemoteControlsPage[NUM_TRACKS][NUM_GROUPS];
      groupBindings = new HardwareBinding[NUM_TRACKS][NUM_GROUPS][];

      for (int t = 0; t < NUM_TRACKS; t++)
      {
         Track track = trackBank.getItemAt(t);
         cursorDevices[t] = track.createCursorDevice("PC12M_DEV_" + t, 0);

         for (int g = 0; g < NUM_GROUPS; g++)
         {
            remoteControls[t][g] = cursorDevices[t].createCursorRemoteControlsPage(
               "PC12M_T" + t + "_G" + (g + 1), NUM_KNOBS_PER_GROUP, "");
            remoteControls[t][g].pageNames().markInterested();
            remoteControls[t][g].selectedPageIndex().markInterested();

            for (int i = 0; i < NUM_KNOBS_PER_GROUP; i++)
            {
               RemoteControl param = remoteControls[t][g].getParameter(i);
               param.markInterested();
               param.name().markInterested();
            }

            groupBindings[t][g] = new HardwareBinding[0];

            // Observer for page name changes
            final int trackIndex = t;
            final int groupIndex = g;
            final String prefix = "FF" + (g + 1);
            remoteControls[t][g].pageNames().addValueObserver(names -> {
               updateTrackGroupBindings(trackIndex, groupIndex, prefix, names);
            });
         }
      }

      // Hardware surface — 72 knobs × 16 channels
      HardwareSurface surface = host.createHardwareSurface();
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

      host.println("Faderfox PC12 MultiInstrument initialized — "
         + NUM_TRACKS + " tracks × " + NUM_GROUPS + " groups");
   }

   private void updateTrackGroupBindings(int trackIndex, int groupIndex,
         String prefix, String[] names)
   {
      // Remove existing bindings for this (track, group)
      for (HardwareBinding binding : groupBindings[trackIndex][groupIndex])
      {
         binding.removeBinding();
      }
      groupBindings[trackIndex][groupIndex] = new HardwareBinding[0];

      // Search for matching FFn or FFn CHc page
      for (int i = 0; i < names.length; i++)
      {
         if (names[i] == null || !names[i].startsWith(prefix))
            continue;

         String rest = names[i].substring(prefix.length());

         // Must be exact match "FFn" or start with space "FFn ..."
         if (rest.length() > 0 && rest.charAt(0) != ' ')
            continue;

         rest = rest.trim();

         // Navigate to this page
         remoteControls[trackIndex][groupIndex].selectedPageIndex().set(i);

         if (rest.isEmpty())
         {
            // Plain "FFn" — bind all 16 channels
            HardwareBinding[] bindings = new HardwareBinding[NUM_CHANNELS * NUM_KNOBS_PER_GROUP];
            int b = 0;
            for (int ch = 0; ch < NUM_CHANNELS; ch++)
            {
               for (int p = 0; p < NUM_KNOBS_PER_GROUP; p++)
               {
                  int knobIndex = groupIndex * NUM_KNOBS_PER_GROUP + p;
                  bindings[b++] = knobs[ch][knobIndex].addBinding(
                     remoteControls[trackIndex][groupIndex].getParameter(p));
               }
            }
            groupBindings[trackIndex][groupIndex] = bindings;

            host.println("Track " + trackIndex + " Group " + (groupIndex + 1)
               + " → page " + i + " (" + names[i] + ") ALL channels");
         }
         else if (rest.startsWith("CH"))
         {
            // "FFn CHc" — bind only channel c
            try
            {
               int channel = Integer.parseInt(rest.substring(2).trim());
               if (channel < 0 || channel >= NUM_CHANNELS)
               {
                  host.println("Track " + trackIndex + " Group " + (groupIndex + 1)
                     + " → page " + i + " (" + names[i] + ") invalid channel " + channel);
                  return;
               }

               HardwareBinding[] bindings = new HardwareBinding[NUM_KNOBS_PER_GROUP];
               for (int p = 0; p < NUM_KNOBS_PER_GROUP; p++)
               {
                  int knobIndex = groupIndex * NUM_KNOBS_PER_GROUP + p;
                  bindings[p] = knobs[channel][knobIndex].addBinding(
                     remoteControls[trackIndex][groupIndex].getParameter(p));
               }
               groupBindings[trackIndex][groupIndex] = bindings;

               host.println("Track " + trackIndex + " Group " + (groupIndex + 1)
                  + " → page " + i + " (" + names[i] + ") CH" + channel + " only");
            }
            catch (NumberFormatException e)
            {
               host.println("Track " + trackIndex + " Group " + (groupIndex + 1)
                  + " → page " + i + " (" + names[i] + ") could not parse channel");
            }
         }
         else
         {
            // "FFn <something else>" — treat as plain FFn match, bind all channels
            HardwareBinding[] bindings = new HardwareBinding[NUM_CHANNELS * NUM_KNOBS_PER_GROUP];
            int b = 0;
            for (int ch = 0; ch < NUM_CHANNELS; ch++)
            {
               for (int p = 0; p < NUM_KNOBS_PER_GROUP; p++)
               {
                  int knobIndex = groupIndex * NUM_KNOBS_PER_GROUP + p;
                  bindings[b++] = knobs[ch][knobIndex].addBinding(
                     remoteControls[trackIndex][groupIndex].getParameter(p));
               }
            }
            groupBindings[trackIndex][groupIndex] = bindings;

            host.println("Track " + trackIndex + " Group " + (groupIndex + 1)
               + " → page " + i + " (" + names[i] + ") ALL channels");
         }
         return;
      }

      host.println("Track " + trackIndex + " Group " + (groupIndex + 1)
         + " → no matching page, unbound");
   }

   @Override
   public void flush()
   {
      // No MIDI out — nothing to send
   }

   @Override
   public void exit()
   {
      getHost().println("Faderfox PC12 MultiInstrument exited");
   }
}
