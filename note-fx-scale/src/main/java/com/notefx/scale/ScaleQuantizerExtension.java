package com.notefx.scale;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.DocumentState;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extension.controller.api.SettableEnumValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScaleQuantizerExtension extends ControllerExtension
{
   private static final String[] ROOT_NAMES =
      {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

   private static final String[] SNAP_NAMES =
      {"Nearest", "Round Up", "Round Down", "Pass Through"};

   private static final String[] INPUT_CHANNEL_NAMES;
   private static final String[] OUTPUT_CHANNEL_NAMES;

   static
   {
      INPUT_CHANNEL_NAMES = new String[17];
      INPUT_CHANNEL_NAMES[0] = "All";
      for (int i = 1; i <= 16; i++) INPUT_CHANNEL_NAMES[i] = "Channel " + i;

      OUTPUT_CHANNEL_NAMES = new String[17];
      OUTPUT_CHANNEL_NAMES[0] = "Same as Input";
      for (int i = 1; i <= 16; i++) OUTPUT_CHANNEL_NAMES[i] = "Channel " + i;
   }

   private ControllerHost host;
   private NoteInput noteInput;

   private int rootIndex = 0;
   private int scaleIndex = 0;
   private int variationIndex = 0;
   private int snapIndex = 0;

   protected ScaleQuantizerExtension(
         ScaleQuantizerDefinition definition, ControllerHost host)
   {
      super(definition, host);
   }

   @Override
   public void init()
   {
      host = getHost();

      MidiIn midiIn = host.getMidiInPort(0);

      // NoteInput passes all note-related MIDI on all channels into Bitwig's engine.
      // The key translation table handles scale quantization.
      noteInput = midiIn.createNoteInput("Scale Quantizer",
         "8?????",   // Note Off, all channels
         "9?????",   // Note On, all channels
         "A?????",   // Poly Aftertouch, all channels
         "D?????",   // Channel Pressure, all channels
         "E?????"    // Pitch Bend, all channels
      );
      noteInput.setShouldConsumeEvents(true);

      // Document state settings — MIDI-learnable, saved per-project
      DocumentState docState = host.getDocumentState();

      SettableEnumValue rootSetting = docState.getEnumSetting(
         "Root Note", "Scale", ROOT_NAMES, "C");
      rootSetting.markInterested();
      rootSetting.addValueObserver(val ->
      {
         rootIndex = indexOf(ROOT_NAMES, val);
         recomputeTable();
      });

      SettableEnumValue scaleSetting = docState.getEnumSetting(
         "Scale", "Scale", ScaleLibrary.NAMES, ScaleLibrary.NAMES[0]);
      scaleSetting.markInterested();
      scaleSetting.addValueObserver(val ->
      {
         scaleIndex = ScaleLibrary.indexOf(val);
         recomputeTable();
      });

      SettableEnumValue variationSetting = docState.getEnumSetting(
         "Variation", "Scale", VariationLibrary.NAMES, VariationLibrary.NAMES[0]);
      variationSetting.markInterested();
      variationSetting.addValueObserver(val ->
      {
         variationIndex = VariationLibrary.indexOf(val);
         recomputeTable();
      });

      SettableEnumValue snapSetting = docState.getEnumSetting(
         "Snap Direction", "Scale", SNAP_NAMES, "Nearest");
      snapSetting.markInterested();
      snapSetting.addValueObserver(val ->
      {
         snapIndex = indexOf(SNAP_NAMES, val);
         recomputeTable();
      });

      // Input/Output channel settings (declared for UI; v1 passes all channels)
      SettableEnumValue inputChannelSetting = docState.getEnumSetting(
         "Input Channel", "Routing", INPUT_CHANNEL_NAMES, "All");
      inputChannelSetting.markInterested();

      SettableEnumValue outputChannelSetting = docState.getEnumSetting(
         "Output Channel", "Routing", OUTPUT_CHANNEL_NAMES, "Same as Input");
      outputChannelSetting.markInterested();

      // Initial table computation (observers may not have fired yet)
      host.scheduleTask(() -> recomputeTable(), 500);

      host.println("Note FX Scale Quantizer v1.0 initialized");
   }

   private void recomputeTable()
   {
      Integer[] table = computeTranslationTable(rootIndex, scaleIndex, variationIndex, snapIndex);
      noteInput.setKeyTranslationTable(table);
   }

   static Integer[] computeTranslationTable(int root, int scaleIdx, int variationIdx, int snapIdx)
   {
      Integer[] table = new Integer[128];

      // Pass Through = identity
      if (snapIdx == 3)
      {
         for (int i = 0; i < 128; i++) table[i] = i;
         return table;
      }

      int[] intervals = ScaleLibrary.INTERVALS[scaleIdx];
      int[] degrees = VariationLibrary.DEGREES[variationIdx];

      // Resolve active intervals from variation degrees
      int[] activeIntervals;
      if (degrees.length == 0)
      {
         activeIntervals = intervals;
      }
      else
      {
         List<Integer> filtered = new ArrayList<>();
         for (int deg : degrees)
         {
            if (deg >= 1 && deg <= intervals.length)
            {
               filtered.add(intervals[deg - 1]);
            }
         }
         if (filtered.isEmpty())
         {
            activeIntervals = intervals;
         }
         else
         {
            activeIntervals = new int[filtered.size()];
            for (int i = 0; i < filtered.size(); i++)
               activeIntervals[i] = filtered.get(i);
         }
      }

      // Build sorted array of all valid MIDI notes (0-127)
      boolean[] valid = new boolean[128];
      for (int octave = -1; octave <= 10; octave++)
      {
         for (int interval : activeIntervals)
         {
            int note = root + octave * 12 + interval;
            if (note >= 0 && note < 128) valid[note] = true;
         }
      }

      int[] validList = new int[128];
      int count = 0;
      for (int i = 0; i < 128; i++)
      {
         if (valid[i]) validList[count++] = i;
      }
      validList = Arrays.copyOf(validList, count);

      // Map each input note to nearest valid note
      for (int i = 0; i < 128; i++)
      {
         if (count == 0)
         {
            table[i] = i;
            continue;
         }

         switch (snapIdx)
         {
            case 0: // Nearest (ties round down)
               table[i] = findNearest(validList, i);
               break;
            case 1: // Round Up
               table[i] = findUp(validList, i);
               break;
            case 2: // Round Down
               table[i] = findDown(validList, i);
               break;
            default:
               table[i] = i;
               break;
         }
      }

      return table;
   }

   private static int findNearest(int[] valid, int note)
   {
      int idx = Arrays.binarySearch(valid, note);
      if (idx >= 0) return valid[idx];

      int ins = -(idx + 1);
      int below = ins > 0 ? valid[ins - 1] : -1;
      int above = ins < valid.length ? valid[ins] : -1;

      if (below < 0) return above;
      if (above < 0) return below;

      int distBelow = note - below;
      int distAbove = above - note;
      return (distBelow <= distAbove) ? below : above;
   }

   private static int findUp(int[] valid, int note)
   {
      int idx = Arrays.binarySearch(valid, note);
      if (idx >= 0) return valid[idx];

      int ins = -(idx + 1);
      if (ins < valid.length) return valid[ins];
      return valid[valid.length - 1];
   }

   private static int findDown(int[] valid, int note)
   {
      int idx = Arrays.binarySearch(valid, note);
      if (idx >= 0) return valid[idx];

      int ins = -(idx + 1);
      if (ins > 0) return valid[ins - 1];
      return valid[0];
   }

   private static int indexOf(String[] array, String value)
   {
      for (int i = 0; i < array.length; i++)
      {
         if (array[i].equals(value)) return i;
      }
      return 0;
   }

   @Override
   public void flush()
   {
      // No outgoing MIDI — NoteInput handles everything
   }

   @Override
   public void exit()
   {
      host.println("Note FX Scale Quantizer exited.");
   }
}
