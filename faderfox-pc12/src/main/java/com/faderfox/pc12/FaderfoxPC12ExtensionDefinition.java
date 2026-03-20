package com.faderfox.pc12;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

import java.util.UUID;

public class FaderfoxPC12ExtensionDefinition extends ControllerExtensionDefinition
{
   private static final UUID DRIVER_ID = UUID.fromString("24788967-112f-47e4-8d60-44a7ce611087");

   @Override
   public String getName() { return "PC12"; }

   @Override
   public String getAuthor() { return "Custom"; }

   @Override
   public String getVersion() { return "1.0.0"; }

   @Override
   public UUID getId() { return DRIVER_ID; }

   @Override
   public String getHardwareVendor() { return "Faderfox"; }

   @Override
   public String getHardwareModel() { return "PC12"; }

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
   public FaderfoxPC12Extension createInstance(ControllerHost host)
   {
      return new FaderfoxPC12Extension(this, host);
   }
}
