package com.electraone.bitwig;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

import java.util.UUID;

public class ElectraOneExtensionDefinition extends ControllerExtensionDefinition
{
   private static final UUID DRIVER_ID =
      UUID.fromString("d4e5f6a7-b8c9-4d0e-a1f2-3b4c5d6e7f80");

   @Override public String getName()           { return "Electra One"; }
   @Override public String getAuthor()         { return "Custom"; }
   @Override public String getVersion()        { return "2.0.0"; }
   @Override public UUID getId()               { return DRIVER_ID; }
   @Override public String getHardwareVendor() { return "Electra One"; }
   @Override public String getHardwareModel()  { return "Electra One"; }
   @Override public int getRequiredAPIVersion() { return 18; }
   @Override public int getNumMidiInPorts()    { return 2; }
   @Override public int getNumMidiOutPorts()   { return 2; }

   @Override
   public void listAutoDetectionMidiPortNames(
         AutoDetectionMidiPortNamesList list, PlatformType platformType)
   {
      switch (platformType)
      {
         case LINUX:
            list.add(
               new String[]{ "Electra Controller Electra Port 1",
                             "Electra Controller Electra CTRL" },
               new String[]{ "Electra Controller Electra Port 1",
                             "Electra Controller Electra CTRL" }
            );
            break;
         case MAC:
         case WINDOWS:
            list.add(
               new String[]{ "Electra Port 1", "Electra CTRL" },
               new String[]{ "Electra Port 1", "Electra CTRL" }
            );
            break;
      }
   }

   @Override
   public ElectraOneExtension createInstance(ControllerHost host)
   {
      return new ElectraOneExtension(this, host);
   }
}
