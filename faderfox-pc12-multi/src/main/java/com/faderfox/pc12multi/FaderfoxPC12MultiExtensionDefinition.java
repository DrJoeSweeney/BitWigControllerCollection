package com.faderfox.pc12multi;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

import java.util.UUID;

public class FaderfoxPC12MultiExtensionDefinition extends ControllerExtensionDefinition
{
   private static final UUID DRIVER_ID = UUID.fromString("a3c7e1b4-9f52-4d8a-b6e0-3f1c82d7a5e9");

   @Override
   public String getName() { return "Faderfox PC12 MultiInstrument"; }

   @Override
   public String getAuthor() { return "Custom"; }

   @Override
   public String getVersion() { return "1.0.0"; }

   @Override
   public UUID getId() { return DRIVER_ID; }

   @Override
   public String getHardwareVendor() { return "DocJoe"; }

   @Override
   public String getHardwareModel() { return "FaderFox PC12 Multi"; }

   @Override
   public int getRequiredAPIVersion() { return 18; }

   @Override
   public int getNumMidiInPorts() { return 1; }

   @Override
   public int getNumMidiOutPorts() { return 0; }

   @Override
   public void listAutoDetectionMidiPortNames(
         AutoDetectionMidiPortNamesList list, PlatformType platformType)
   {
      // No auto-detection — user selects the MIDI port manually
   }

   @Override
   public FaderfoxPC12MultiExtension createInstance(ControllerHost host)
   {
      return new FaderfoxPC12MultiExtension(this, host);
   }
}
