package com.electraone.bitwig;

import com.bitwig.extension.controller.api.ControllerHost;
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
   private final ControllerHost host;

   // Log first N SysEx sends to verify format, then go quiet
   private int sendCount = 0;
   private static final int LOG_LIMIT = 10;

   public ElectraOneSysEx(MidiOut ctrlOut, ControllerHost host)
   {
      this.ctrlOut = ctrlOut;
      this.host = host;
   }

   /**
    * Send an updateRuntime SysEx message to update a control's name
    * and displayed value on the E1 screen.
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
      // Build compact hex string: F0 00 21 45 14 07 <json> F7
      // 0x14 = Data command, 0x07 = RuntimeInfo subcommand
      StringBuilder sb = new StringBuilder();
      sb.append("f00021451407");

      for (int i = 0; i < json.length(); i++)
      {
         char c = json.charAt(i);
         int b = c & 0x7F;
         sb.append(String.format("%02x", b));
      }
      sb.append("f7");

      String sysexHex = sb.toString();

      // Log first few sends so we can verify the SysEx format
      sendCount++;
      if (sendCount <= LOG_LIMIT)
      {
         host.println("SysEx SEND #" + sendCount + " json=" + json);
         host.println("  hex=" + sysexHex.substring(0, Math.min(120, sysexHex.length()))
            + (sysexHex.length() > 120 ? "..." : ""));
      }

      // Send on CTRL port only (port 1)
      ctrlOut.sendSysex(sysexHex);
   }

   /**
    * Parse a section switch SysEx message from the E1 touchscreen.
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
}
