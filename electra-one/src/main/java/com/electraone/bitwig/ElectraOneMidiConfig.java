package com.electraone.bitwig;

/**
 * MIDI CC assignments and port indices for the Electra One controller.
 *
 * Knob layout (2x6 grid):
 *   Row 1: [1-Track] [2-P0] [3-P1] [4-P2] [5-P3] [6-Device]
 *   Row 2: [7-Page]  [8-P4] [9-P5] [10-P6] [11-P7] [12-Bank]
 */
public final class ElectraOneMidiConfig
{
   private ElectraOneMidiConfig() {}

   // MIDI port indices
   public static final int PORT_MIDI = 0;
   public static final int PORT_CTRL = 1;

   // MIDI channel (0-indexed)
   public static final int CHANNEL = 0;

   // Navigation knob CCs (relative 2's complement)
   public static final int CC_TRACK  = 0;   // Knob 1 — prev/next track
   public static final int CC_DEVICE = 5;   // Knob 6 — prev/next device
   public static final int CC_PAGE   = 6;   // Knob 7 — prev/next page
   public static final int CC_BANK   = 11;  // Knob 12 — prev/next page bank

   // Parameter knob CCs (absolute, MSB)
   // Params 0-3: CC 1-4, Params 4-7: CC 7-10
   public static final int[] PARAM_CC_MSB = { 1, 2, 3, 4, 7, 8, 9, 10 };

   // 14-bit LSB CCs (MSB CC + 32)
   public static final int[] PARAM_CC_LSB = { 33, 34, 35, 36, 39, 40, 41, 42 };

   public static final int NUM_PARAMS = 8;
   public static final int NUM_SECTIONS = 3;
   public static final int ENCODER_STEP_SIZE = 128;

   // SysEx constants
   public static final byte[] MANUFACTURER_ID = { 0x00, 0x21, 0x45 };

   // Section switch SysEx: F0 00 21 45 7E 07 [01|02|03] F7
   public static final String SECTION_SYSEX_PREFIX = "f000214500";  // verified below
}
