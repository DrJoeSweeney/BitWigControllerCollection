package com.notefx.scale;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

import java.util.UUID;

public class ScaleQuantizerDefinition extends ControllerExtensionDefinition
{
   private static final UUID DRIVER_ID =
      UUID.fromString("fbadfac5-f9e5-4d87-8dc3-0592e59e2a38");

   @Override public String getName()            { return "Note FX Scale Quantizer"; }
   @Override public String getAuthor()          { return "DocJoe"; }
   @Override public String getVersion()         { return "1.0.0"; }
   @Override public UUID getId()                { return DRIVER_ID; }
   @Override public String getHardwareVendor()  { return "DocJoe"; }
   @Override public String getHardwareModel()   { return "Note FX Scale Quantizer"; }
   @Override public int getRequiredAPIVersion() { return 18; }
   @Override public int getNumMidiInPorts()     { return 1; }
   @Override public int getNumMidiOutPorts()    { return 0; }

   @Override
   public void listAutoDetectionMidiPortNames(
         AutoDetectionMidiPortNamesList list, PlatformType platformType)
   {
      // No auto-detection — user selects the MIDI input port manually
   }

   @Override
   public ScaleQuantizerExtension createInstance(ControllerHost host)
   {
      return new ScaleQuantizerExtension(this, host);
   }
}
