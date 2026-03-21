package com.electraone.bitwig;

import com.bitwig.extension.controller.api.MidiOut;

/**
 * Builds and sends SysEx messages to the Electra One controller.
 * All SysEx is sent on port 1 (CTRL).
 *
 * Manufacturer ID: 0x00 0x21 0x45
 * Update runtime command: F0 00 21 45 14 07 <JSON> F7
 */
public class ElectraOneSysEx
{
   private final MidiOut ctrlOut;

   public ElectraOneSysEx(MidiOut ctrlOut)
   {
      this.ctrlOut = ctrlOut;
   }

   /**
    * Send an updateRuntime SysEx message with JSON payload to update
    * a control's name and displayed value on the E1 screen.
    *
    * @param controlId the E1 control ID (from preset)
    * @param name      parameter name to display
    * @param value     formatted parameter value to display
    */
   public void sendControlUpdate(int controlId, String name, String value)
   {
      String json = "{\"controlId\":" + controlId
         + ",\"name\":\"" + escapeJson(name)
         + "\",\"value\":{\"id\":\"value\",\"text\":\"" + escapeJson(value) + "\"}}";

      sendUpdateRuntime(json);
   }

   /**
    * Send an updateRuntime SysEx message with arbitrary JSON payload.
    */
   public void sendUpdateRuntime(String json)
   {
      StringBuilder sb = new StringBuilder();
      sb.append("F0 00 21 45 14 07 ");

      for (int i = 0; i < json.length(); i++)
      {
         char c = json.charAt(i);
         // SysEx data bytes must be 0-127; ASCII printable chars are fine
         int b = c & 0x7F;
         sb.append(String.format("%02X ", b));
      }
      sb.append("F7");

      ctrlOut.sendSysex(sb.toString());
   }

   /**
    * Escape special characters for JSON strings.
    * Replaces backslash, double-quote, and control characters.
    */
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
            case '\n': sb.append("\\n"); break;
            case '\r': sb.append("\\r"); break;
            case '\t': sb.append("\\t"); break;
            default:
               if (c < 0x20)
                  sb.append(' ');
               else
                  sb.append(c);
               break;
         }
      }
      return sb.toString();
   }

   /**
    * Parse a section switch SysEx message.
    * Expected format: F0 00 21 45 7E 07 [01|02|03] F7
    * @param data hex string from setSysexCallback
    * @return section number (0-2), or -1 if not a section switch message
    */
   public static int parseSectionSwitch(String data)
   {
      // Normalize: remove spaces, lowercase
      String hex = data.replaceAll("\\s+", "").toLowerCase();

      // Check prefix: f0002145 7e 07
      if (!hex.startsWith("f00021457e07"))
         return -1;

      if (hex.length() < 16)
         return -1;

      // Extract section byte (2 hex chars after prefix)
      String sectionHex = hex.substring(12, 14);
      try
      {
         int section = Integer.parseInt(sectionHex, 16);
         if (section >= 1 && section <= 3)
            return section - 1; // Convert to 0-indexed
      }
      catch (NumberFormatException e)
      {
         // Not a valid section
      }
      return -1;
   }
}
