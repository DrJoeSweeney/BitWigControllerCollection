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
 */
public class ElectraOneSysEx
{
   private final MidiOut ctrlOut;
   private final ControllerHost host;

   private int sendCount = 0;
   private static final int LOG_LIMIT = 10;

   // SysEx header (hex)
   private static final String HDR = "f0002145";

   // Command bytes (hex)
   private static final String CMD_CONTROL_UPDATE    = "1407";  // name, color, visible
   private static final String CMD_VALUE_LABEL       = "140e";  // value text
   private static final String CMD_REPAINT_ENABLED   = "7f7a";  // enable/disable repaint

   public ElectraOneSysEx(MidiOut ctrlOut, ControllerHost host)
   {
      this.ctrlOut = ctrlOut;
      this.host = host;
   }

   /**
    * Send a control update to set the name, and a value label update
    * to set the displayed value text on the E1 screen.
    */
   public void sendControlUpdate(int controlId, String name, String value)
   {
      sendControlName(controlId, name);
      sendValueLabel(controlId, value);
   }

   /**
    * Send a control name update via 0x14 0x07 command.
    * Format: F0 00 21 45 14 07 [id_lo] [id_hi] {"name":"..."} F7
    */
   public void sendControlName(int controlId, String name)
   {
      String json = "{\"name\":\"" + escapeJson(name) + "\"}";
      String hex = HDR + CMD_CONTROL_UPDATE
         + encodeControlId(controlId)
         + asciiToHex(json) + "f7";

      logSend("name", controlId, name, hex);
      ctrlOut.sendSysex(hex);
   }

   /**
    * Send a value label update via 0x14 0x0E command.
    * Format: F0 00 21 45 14 0E [id_lo] [id_hi] 00 <ascii text> F7
    * The 00 byte is the value index (first/default value slot).
    */
   public void sendValueLabel(int controlId, String text)
   {
      String hex = HDR + CMD_VALUE_LABEL
         + encodeControlId(controlId)
         + "00"  // value index
         + asciiToHex(text) + "f7";

      logSend("value", controlId, text, hex);
      ctrlOut.sendSysex(hex);
   }

   /**
    * Enable or disable E1 display repainting.
    * Call setRepaintEnabled(false) before batch updates,
    * then setRepaintEnabled(true) after to trigger a single repaint.
    */
   public void setRepaintEnabled(boolean enabled)
   {
      String hex = HDR + CMD_REPAINT_ENABLED
         + (enabled ? "01" : "00") + "f7";
      ctrlOut.sendSysex(hex);
   }

   /**
    * Send an arbitrary updateRuntime JSON payload (legacy format).
    */
   public void sendUpdateRuntime(String json)
   {
      String hex = HDR + CMD_CONTROL_UPDATE
         + asciiToHex(json) + "f7";

      sendCount++;
      if (sendCount <= LOG_LIMIT)
      {
         host.println("SysEx SEND #" + sendCount + " json=" + json);
         host.println("  hex=" + hex.substring(0, Math.min(120, hex.length()))
            + (hex.length() > 120 ? "..." : ""));
      }
      ctrlOut.sendSysex(hex);
   }

   /**
    * Encode a control ID as two 7-bit bytes (little-endian).
    * Returns 4-char hex string (2 bytes).
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

   private void logSend(String type, int controlId, String text, String hex)
   {
      sendCount++;
      if (sendCount <= LOG_LIMIT)
      {
         host.println("SysEx SEND #" + sendCount + " " + type
            + " id=" + controlId + " \"" + text + "\"");
         host.println("  hex=" + hex.substring(0, Math.min(100, hex.length()))
            + (hex.length() > 100 ? "..." : ""));
      }
   }

   /**
    * Parse a section switch SysEx from the E1 touchscreen.
    * Expected format: F0 00 21 45 7E 07 [01|02|03] F7
    */
   public static int parseSectionSwitch(String data)
   {
      String hex = data.replaceAll("\\s+", "").toLowerCase();

      if (!hex.startsWith("f00021457e07"))
         return -1;

      if (hex.length() < 16)
         return -1;

      String sectionHex = hex.substring(12, 14);
      try
      {
         int section = Integer.parseInt(sectionHex, 16);
         if (section >= 1 && section <= 3)
            return section - 1;
      }
      catch (NumberFormatException e)
      {
         // Not a valid section
      }
      return -1;
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
               if (c >= 0x20 && c < 0x7F)
                  sb.append(c);
               else
                  sb.append(' ');
               break;
         }
      }
      return sb.toString();
   }
}
