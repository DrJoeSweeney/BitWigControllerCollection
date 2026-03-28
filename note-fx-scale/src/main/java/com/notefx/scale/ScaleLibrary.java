package com.notefx.scale;

public class ScaleLibrary
{
   public static final String[] NAMES =
   {
      // Diatonic Modes (7)
      "Major (Ionian)",
      "Dorian",
      "Phrygian",
      "Lydian",
      "Mixolydian",
      "Aeolian (Natural Minor)",
      "Locrian",

      // Harmonic Minor Modes (7)
      "Harmonic Minor",
      "Locrian nat6",
      "Ionian #5",
      "Dorian #4",
      "Phrygian Dominant",
      "Lydian #2",
      "Ultralocrian",

      // Melodic Minor Modes (7)
      "Melodic Minor",
      "Dorian b2",
      "Lydian Augmented",
      "Lydian Dominant",
      "Mixolydian b6",
      "Locrian nat2",
      "Altered (Super Locrian)",

      // Pentatonic & Blues (7)
      "Major Pentatonic",
      "Minor Pentatonic",
      "Blues Major",
      "Blues Minor",
      "Hirajoshi",
      "Insen",
      "Yo",

      // Symmetric (5)
      "Whole Tone",
      "Diminished HW",
      "Diminished WH",
      "Augmented",
      "Tritone",

      // World (12)
      "Byzantine (Double Harmonic)",
      "Hungarian Minor",
      "Hungarian Major",
      "Neapolitan Minor",
      "Neapolitan Major",
      "Persian",
      "Arabian",
      "Enigmatic",
      "Flamenco",
      "Spanish 8-Tone",

      // Bebop (4)
      "Bebop Dominant",
      "Bebop Major",
      "Bebop Dorian",
      "Bebop Minor",

      // Chromatic (1)
      "Chromatic",

      // Additional (1)
      "Prometheus",
   };

   public static final int[][] INTERVALS =
   {
      // Diatonic Modes
      {0, 2, 4, 5, 7, 9, 11},      // Major (Ionian)
      {0, 2, 3, 5, 7, 9, 10},      // Dorian
      {0, 1, 3, 5, 7, 8, 10},      // Phrygian
      {0, 2, 4, 6, 7, 9, 11},      // Lydian
      {0, 2, 4, 5, 7, 9, 10},      // Mixolydian
      {0, 2, 3, 5, 7, 8, 10},      // Aeolian (Natural Minor)
      {0, 1, 3, 5, 6, 8, 10},      // Locrian

      // Harmonic Minor Modes
      {0, 2, 3, 5, 7, 8, 11},      // Harmonic Minor
      {0, 1, 3, 5, 6, 9, 10},      // Locrian nat6
      {0, 2, 4, 5, 8, 9, 11},      // Ionian #5
      {0, 2, 3, 6, 7, 9, 10},      // Dorian #4
      {0, 1, 4, 5, 7, 8, 10},      // Phrygian Dominant
      {0, 3, 4, 6, 7, 9, 11},      // Lydian #2
      {0, 1, 3, 4, 6, 8, 9},       // Ultralocrian

      // Melodic Minor Modes
      {0, 2, 3, 5, 7, 9, 11},      // Melodic Minor
      {0, 1, 3, 5, 7, 9, 10},      // Dorian b2
      {0, 2, 4, 6, 8, 9, 11},      // Lydian Augmented
      {0, 2, 4, 6, 7, 9, 10},      // Lydian Dominant
      {0, 2, 4, 5, 7, 8, 10},      // Mixolydian b6
      {0, 2, 3, 5, 6, 8, 10},      // Locrian nat2
      {0, 1, 3, 4, 6, 8, 10},      // Altered (Super Locrian)

      // Pentatonic & Blues
      {0, 2, 4, 7, 9},             // Major Pentatonic
      {0, 3, 5, 7, 10},            // Minor Pentatonic
      {0, 2, 3, 4, 7, 9},          // Blues Major
      {0, 3, 5, 6, 7, 10},         // Blues Minor
      {0, 2, 3, 7, 8},             // Hirajoshi
      {0, 1, 5, 7, 10},            // Insen
      {0, 2, 5, 7, 9},             // Yo

      // Symmetric
      {0, 2, 4, 6, 8, 10},         // Whole Tone
      {0, 1, 3, 4, 6, 7, 9, 10},   // Diminished HW
      {0, 2, 3, 5, 6, 8, 9, 11},   // Diminished WH
      {0, 3, 4, 7, 8, 11},         // Augmented
      {0, 1, 4, 6, 7, 10},         // Tritone

      // World
      {0, 1, 4, 5, 7, 8, 11},      // Byzantine (Double Harmonic)
      {0, 2, 3, 6, 7, 8, 11},      // Hungarian Minor
      {0, 3, 4, 6, 7, 9, 10},      // Hungarian Major
      {0, 1, 3, 5, 7, 8, 11},      // Neapolitan Minor
      {0, 1, 3, 5, 7, 9, 11},      // Neapolitan Major
      {0, 1, 4, 5, 6, 8, 11},      // Persian
      {0, 2, 4, 5, 6, 8, 10},      // Arabian
      {0, 1, 4, 6, 8, 10, 11},     // Enigmatic
      {0, 1, 3, 4, 5, 7, 8, 10},   // Flamenco
      {0, 1, 3, 4, 5, 6, 8, 10},   // Spanish 8-Tone

      // Bebop
      {0, 2, 4, 5, 7, 9, 10, 11},  // Bebop Dominant
      {0, 2, 4, 5, 7, 8, 9, 11},   // Bebop Major
      {0, 2, 3, 4, 5, 7, 9, 10},   // Bebop Dorian
      {0, 2, 3, 5, 7, 8, 9, 10},   // Bebop Minor

      // Chromatic
      {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},  // Chromatic

      // Additional
      {0, 2, 4, 6, 9, 10},         // Prometheus
   };

   public static int indexOf(String name)
   {
      for (int i = 0; i < NAMES.length; i++)
      {
         if (NAMES[i].equals(name)) return i;
      }
      return 0;
   }
}
