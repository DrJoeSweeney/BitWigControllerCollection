#pragma once

#define NUM_SCALES 49

static const char *scale_names[NUM_SCALES] = {
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
   // World (10)
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

// Max 12 intervals per scale. -1 sentinel terminates.
static const int8_t scale_intervals[NUM_SCALES][13] = {
   // Diatonic
   {0,2,4,5,7,9,11,-1},
   {0,2,3,5,7,9,10,-1},
   {0,1,3,5,7,8,10,-1},
   {0,2,4,6,7,9,11,-1},
   {0,2,4,5,7,9,10,-1},
   {0,2,3,5,7,8,10,-1},
   {0,1,3,5,6,8,10,-1},
   // Harmonic Minor
   {0,2,3,5,7,8,11,-1},
   {0,1,3,5,6,9,10,-1},
   {0,2,4,5,8,9,11,-1},
   {0,2,3,6,7,9,10,-1},
   {0,1,4,5,7,8,10,-1},
   {0,3,4,6,7,9,11,-1},
   {0,1,3,4,6,8,9,-1},
   // Melodic Minor
   {0,2,3,5,7,9,11,-1},
   {0,1,3,5,7,9,10,-1},
   {0,2,4,6,8,9,11,-1},
   {0,2,4,6,7,9,10,-1},
   {0,2,4,5,7,8,10,-1},
   {0,2,3,5,6,8,10,-1},
   {0,1,3,4,6,8,10,-1},
   // Pentatonic & Blues
   {0,2,4,7,9,-1},
   {0,3,5,7,10,-1},
   {0,2,3,4,7,9,-1},
   {0,3,5,6,7,10,-1},
   {0,2,3,7,8,-1},
   {0,1,5,7,10,-1},
   {0,2,5,7,9,-1},
   // Symmetric
   {0,2,4,6,8,10,-1},
   {0,1,3,4,6,7,9,10,-1},
   {0,2,3,5,6,8,9,11,-1},
   {0,3,4,7,8,11,-1},
   {0,1,4,6,7,10,-1},
   // World
   {0,1,4,5,7,8,11,-1},
   {0,2,3,6,7,8,11,-1},
   {0,3,4,6,7,9,10,-1},
   {0,1,3,5,7,8,11,-1},
   {0,1,3,5,7,9,11,-1},
   {0,1,4,5,6,8,11,-1},
   {0,2,4,5,6,8,10,-1},
   {0,1,4,6,8,10,11,-1},
   {0,1,3,4,5,7,8,10,-1},
   {0,1,3,4,5,6,8,10,-1},
   // Bebop
   {0,2,4,5,7,9,10,11,-1},
   {0,2,4,5,7,8,9,11,-1},
   {0,2,3,4,5,7,9,10,-1},
   {0,2,3,5,7,8,9,10,-1},
   // Chromatic
   {0,1,2,3,4,5,6,7,8,9,10,11,-1},
   // Prometheus
   {0,2,4,6,9,10,-1},
};
