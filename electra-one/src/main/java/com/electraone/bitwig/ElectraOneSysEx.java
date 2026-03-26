package com.electraone.bitwig;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.MidiOut;

/**
 * Builds and sends SysEx messages to the Electra One controller.
 * All SysEx is sent on port 1 (CTRL).
 *
 * Manufacturer ID: 0x00 0x21 0x45
 *
 * Control update (name/color):  F0 00 21 45 14 07 [id_lo] [id_hi] {json} F7
 * Value label update:           F0 00 21 45 14 0E [id_lo] [id_hi] 00 <text> F7
 * Repaint enable/disable:       F0 00 21 45 7F 7A [00|01] F7
 * Event subscription:           F0 00 21 45 14 79 [flags] F7
 */
public class ElectraOneSysEx
{
   private final MidiOut ctrlOut;
   private final ControllerHost host;

   // SysEx header (hex)
   private static final String HDR = "f0002145";

   // Command bytes (hex)
   private static final String CMD_CONTROL_UPDATE  = "1407";
   private static final String CMD_VALUE_LABEL     = "140e";
   private static final String CMD_REPAINT         = "7f7a";
   private static final String CMD_SUBSCRIBE       = "1479";

   // Event subscription flags: pages (0x01) + pots/touch (0x08) + buttons (0x20)
   private static final String EVENT_FLAGS = "29";

   public ElectraOneSysEx(MidiOut ctrlOut, ControllerHost host)
   {
      this.ctrlOut = ctrlOut;
      this.host = host;
   }

   /**
    * Send a control update with name and color.
    * JSON payload: {"name":"...","color":"RRGGBB"}
    */
   public void sendControlNameColor(int controlId, String name, String color)
   {
      String json = "{\"name\":\"" + escapeJson(name)
         + "\",\"color\":\"" + color + "\"}";
      String hex = HDR + CMD_CONTROL_UPDATE
         + encodeControlId(controlId)
         + asciiToHex(json) + "f7";
      ctrlOut.sendSysex(hex);
   }

   /**
    * Send a value label update (text shown below the knob).
    * Format: F0 00 21 45 14 0E [id_lo] [id_hi] 00 <ascii text> F7
    */
   public void sendValueLabel(int controlId, String text)
   {
      String hex = HDR + CMD_VALUE_LABEL
         + encodeControlId(controlId)
         + "00"  // value index (primary value slot)
         + asciiToHex(text) + "f7";
      ctrlOut.sendSysex(hex);
   }

   /**
    * Enable or disable E1 display repainting.
    * Disable before batch updates, enable after to trigger one repaint.
    */
   public void setRepaintEnabled(boolean enabled)
   {
      String hex = HDR + CMD_REPAINT
         + (enabled ? "01" : "00") + "f7";
      ctrlOut.sendSysex(hex);
   }

   /**
    * Subscribe to E1 events (page switches, pot touch, buttons).
    * Must be called during init for the E1 to send these events.
    */
   public void subscribeToEvents()
   {
      String hex = HDR + CMD_SUBSCRIBE + EVENT_FLAGS + "f7";
      ctrlOut.sendSysex(hex);
      host.println("Subscribed to E1 events");
   }

   /**
    * Parse a section/page switch SysEx from the E1 touchscreen.
    * Format: F0 00 21 45 7E 07 [01|02|03] F7
    * Returns 0-2 for sections 1-3, or -1 if not a section switch.
    */
   public static int parseSectionSwitch(String data)
   {
      String hex = data.replaceAll("\\s+", "").toLowerCase();
      if (!hex.startsWith("f00021457e07")) return -1;
      if (hex.length() < 16) return -1;

      try
      {
         int section = Integer.parseInt(hex.substring(12, 14), 16);
         if (section >= 1 && section <= 3) return section - 1;
      }
      catch (NumberFormatException e) { /* not a valid section */ }
      return -1;
   }

   /**
    * Parse a pot touch SysEx event from the E1.
    * Format: F0 00 21 45 7E 0A [potId] [ctrlId_lo] [ctrlId_hi] [touched] F7
    * Returns the potId (1-12) on touch-down, or -1 otherwise.
    */
   public static int parsePotTouch(String data)
   {
      String hex = data.replaceAll("\\s+", "").toLowerCase();
      if (!hex.startsWith("f00021457e0a")) return -1;
      if (hex.length() < 22) return -1;

      try
      {
         int potId = Integer.parseInt(hex.substring(12, 14), 16);
         int touched = Integer.parseInt(hex.substring(18, 20), 16);
         if (touched != 0) return potId;  // touch-down
      }
      catch (NumberFormatException e) { /* invalid */ }
      return -1;
   }

   // ── Helpers ───────────────────────────────────────────────────────────

   /**
    * Encode a control ID as two 7-bit bytes (little-endian).
    */
   private static String encodeControlId(int controlId)
   {
      int lo = controlId & 0x7F;
      int hi = controlId >> 7;
      return String.format("%02x%02x", lo, hi);
   }

   /**
    * Convert an ASCII string to hex bytes.
    */
   private static String asciiToHex(String s)
   {
      if (s == null || s.isEmpty()) return "";
      StringBuilder sb = new StringBuilder(s.length() * 2);
      for (int i = 0; i < s.length(); i++)
      {
         int b = s.charAt(i) & 0x7F;
         sb.append(String.format("%02x", b));
      }
      return sb.toString();
   }

   private static String escapeJson(String s)
   {
      if (s == null) return "";
      StringBuilder sb = new StringBuilder(s.length());
      for (int i = 0; i < s.length(); i++)
      {
         char c = s.charAt(i);
         switch (c)
         {
            case '"':  sb.append("\\\""); break;
            case '\\': sb.append("\\\\"); break;
            default:
               if (c >= 0x20 && c < 0x7F) sb.append(c);
               else sb.append(' ');
               break;
         }
      }
      return sb.toString();
   }
}
