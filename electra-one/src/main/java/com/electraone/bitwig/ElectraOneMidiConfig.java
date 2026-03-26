package com.electraone.bitwig;

/**
 * MIDI CC assignments and constants for the Electra One controller.
 *
 * Knob layout per control set (2 rows x 6 knobs):
 *   Row A: [A1-Page]   [A2-P0] [A3-P1] [A4-P2] [A5-P3] [A6-Track]
 *   Row B: [B1-Device] [B2-P4] [B3-P5] [B4-P6] [B5-P7] [B6-Volume]
 *
 * 3 control sets on the E1 touchscreen show 3 consecutive Remote Controls pages.
 * Corner knobs (A1, A6, B1, B6) are navigation — relative 2's complement.
 * Inner knobs (A2-A5, B2-B5) are parameters — absolute CC (0-127).
 *
 * Each control set uses DIFFERENT parameter CCs so the E1 preset doesn't
 * merge controls with duplicate parameterIds. Navigation CCs are shared.
 */
public final class ElectraOneMidiConfig
{
   private ElectraOneMidiConfig() {}

   // MIDI port indices (E1 exposes two: MIDI data + CTRL/SysEx)
   public static final int PORT_MIDI = 0;
   public static final int PORT_CTRL = 1;

   // MIDI channel (0-indexed, i.e. channel 1)
   public static final int CHANNEL = 0;

   // Navigation knob CCs (relative 2's complement: 1-63 = CW, 65-127 = CCW)
   // Same CC across all 3 control sets — they do the same thing.
   public static final int CC_PAGE   = 0;   // A1 — prev/next remote controls page
   public static final int CC_TRACK  = 5;   // A6 — prev/next track
   public static final int CC_DEVICE = 6;   // B1 — prev/next device
   public static final int CC_VOLUME = 11;  // B6 — track volume (rotation) + play/stop (touch)

   // Parameter knob CCs per section (absolute 0-127).
   // Each set uses unique CCs so the E1 preset has no duplicate parameterIds.
   // Params 0-3 map to A2-A5, Params 4-7 map to B2-B5.
   public static final int[][] SECTION_PARAM_CC = {
      {  1,  2,  3,  4,  7,  8,  9, 10 },   // Set 1
      { 14, 15, 16, 17, 20, 21, 22, 23 },   // Set 2
      { 26, 27, 28, 29, 32, 33, 34, 35 }    // Set 3
   };

   // Layout constants
   public static final int NUM_PARAMS = 8;
   public static final int NUM_SECTIONS = 3;
   public static final int CONTROLS_PER_SECTION = 12;

   // E1 control offsets within a 12-control section (0-indexed)
   // These match the physical knob positions in the 2x6 grid
   public static final int[] PARAM_OFFSET = { 1, 2, 3, 4, 7, 8, 9, 10 };

   // Navigation control offsets within a section
   public static final int NAV_PAGE_OFFSET   = 0;   // A1
   public static final int NAV_TRACK_OFFSET  = 5;   // A6
   public static final int NAV_DEVICE_OFFSET = 6;   // B1
   public static final int NAV_VOLUME_OFFSET = 11;  // B6

   // Section colors (hex RGB for E1 display)
   public static final String[] SECTION_COLORS = { "F45C51", "ED8F20", "529DEC" };
   public static final String NAV_COLOR = "FFFFFF";
   public static final String PLAY_COLOR = "03A60E";

   // E1 pot ID for B6 (used to detect touch for play/stop)
   public static final int POT_B6 = 12;
}
