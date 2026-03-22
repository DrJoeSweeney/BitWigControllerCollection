package com.electraone.bitwig;

/**
 * MIDI CC assignments and port indices for the Electra One controller.
 *
 * Knob layout per control set (2 rows x 6 knobs):
 *   Row A: [A1-Track] [A2-P0] [A3-P1] [A4-P2] [A5-P3] [A6-Device]
 *   Row B: [B1-Page]  [B2-P4] [B3-P5] [B4-P6] [B5-P7] [B6-Bank]
 *
 * 3 control sets on the E1 touchscreen show 3 consecutive pages.
 * Corner knobs (A1, A6, B1, B6) are navigation — relative 2's complement.
 * Inner knobs (A2-A5, B2-B5) are parameters — absolute CC.
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
   public static final int CC_TRACK  = 0;   // A1 — prev/next track
   public static final int CC_DEVICE = 5;   // A6 — prev/next device
   public static final int CC_PAGE   = 6;   // B1 — prev/next page
   public static final int CC_BANK   = 11;  // B6 — page bank jump (±3)

   // Parameter knob CCs (absolute, MSB)
   // Params 0-3: CC 1-4 (A2-A5), Params 4-7: CC 7-10 (B2-B5)
   public static final int[] PARAM_CC_MSB = { 1, 2, 3, 4, 7, 8, 9, 10 };

   // 14-bit LSB CCs (MSB CC + 32)
   public static final int[] PARAM_CC_LSB = { 33, 34, 35, 36, 39, 40, 41, 42 };

   public static final int NUM_PARAMS = 8;
   public static final int NUM_SECTIONS = 3;
   public static final int CONTROLS_PER_SECTION = 12;

   // SysEx constants
   public static final byte[] MANUFACTURER_ID = { 0x00, 0x21, 0x45 };
}
