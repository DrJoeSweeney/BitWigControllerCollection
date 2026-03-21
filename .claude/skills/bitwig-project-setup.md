---
description: Scaffolding and configuring new Bitwig controller extension projects — Maven setup, pom.xml, directory structure, ControllerExtensionDefinition, ControllerExtension skeleton
triggers:
  - creating a new Bitwig controller extension
  - scaffolding a project
  - Maven setup for Bitwig
  - pom.xml for Bitwig extension
  - directory structure for controller extension
  - new extension project
  - ControllerExtensionDefinition
  - bwextension
---

# Bitwig Controller Extension — Project Setup

## Canonical Directory Structure

```
my-controller/
├── pom.xml
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── mycontroller/
│       │               ├── MyControllerExtensionDefinition.java
│       │               └── MyControllerExtension.java
│       └── resources/
│           └── META-INF/
│               └── services/
│                   └── com.bitwig.extension.ExtensionDefinition
└── target/
    └── MyController.bwextension          (build output)
```

## CRITICAL: ServiceLoader Registration

Bitwig uses Java's ServiceLoader to discover extensions. You **must** create this file:

`src/main/resources/META-INF/services/com.bitwig.extension.ExtensionDefinition`

Contents — one line with the fully qualified class name of your definition:

```
com.example.mycontroller.MyControllerExtensionDefinition
```

**Without this file, Bitwig will fail with "No extensions found" even though the .bwextension file is in the correct folder.**

## pom.xml Template

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-controller</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>My Controller</name>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>12</maven.compiler.source>
        <maven.compiler.target>12</maven.compiler.target>
        <bitwig.extension.directory>${project.basedir}/../Extensions</bitwig.extension.directory>
    </properties>

    <repositories>
        <repository>
            <id>bitwig</id>
            <name>Bitwig Maven Repository</name>
            <url>https://maven.bitwig.com</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>com.bitwig</groupId>
            <artifactId>extension-api</artifactId>
            <version>18</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Compile -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
            </plugin>

            <!-- Package as .bwextension (renamed jar) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <finalName>${project.artifactId}</finalName>
                    <!-- .bwextension is just a jar with a different extension -->
                </configuration>
            </plugin>

            <!-- Rename .jar to .bwextension and copy to Bitwig Extensions folder -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-antrun-plugin</artifactId>
                <version>3.1.0</version>
                <executions>
                    <execution>
                        <id>deploy-extension</id>
                        <phase>install</phase>
                        <goals><goal>run</goal></goals>
                        <configuration>
                            <target>
                                <copy file="${project.build.directory}/${project.artifactId}.jar"
                                      tofile="${bitwig.extension.directory}/${project.artifactId}.bwextension"
                                      overwrite="true"/>
                            </target>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### Notes on pom.xml
- **API version**: Check https://maven.bitwig.com for the latest `extension-api` version. Version 18 corresponds to Bitwig 5.x. Adjust to match your Bitwig Studio version.
- **Java version**: Bitwig requires Java 12+. Use 17 if available.
- **Deploy path**: The `bitwig.extension.directory` property auto-deploys on `mvn install` to `../Extensions/` (relative to the module).

## ControllerExtensionDefinition — The Descriptor

This class tells Bitwig about your controller. It is loaded even before the extension is activated.

```java
package com.example.mycontroller;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

import java.util.UUID;

public class MyControllerExtensionDefinition extends ControllerExtensionDefinition
{
   // Generate a unique UUID for your extension — run: uuidgen (Linux/macOS)
   private static final UUID DRIVER_ID = UUID.fromString("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");

   @Override
   public String getName() { return "My Controller"; }

   @Override
   public String getAuthor() { return "Your Name"; }

   @Override
   public String getVersion() { return "1.0.0"; }

   @Override
   public UUID getId() { return DRIVER_ID; }

   @Override
   public String getHardwareVendor() { return "Hardware Vendor"; }

   @Override
   public String getHardwareModel() { return "Hardware Model"; }

   @Override
   public int getRequiredAPIVersion() { return 18; }

   @Override
   public int getNumMidiInPorts() { return 1; }

   @Override
   public int getNumMidiOutPorts() { return 1; }

   @Override
   public void listAutoDetectionMidiPortNames(
         AutoDetectionMidiPortNamesList list, PlatformType platformType)
   {
      // Add port names that Bitwig should look for to auto-detect this controller.
      // Use platform-specific names if needed.
      // Example:
      // if (platformType == PlatformType.WINDOWS)
      //    list.add(new String[]{"My Controller"}, new String[]{"My Controller"});
      // else
      //    list.add(new String[]{"My Controller MIDI 1"}, new String[]{"My Controller MIDI 1"});
   }

   @Override
   public MyControllerExtension createInstance(ControllerHost host)
   {
      return new MyControllerExtension(this, host);
   }
}
```

### UUID Generation
Every extension needs a globally unique ID. Generate one:
```bash
# Linux / macOS
uuidgen

# Or use Java
java -e "System.out.println(java.util.UUID.randomUUID())"
```
**Never reuse UUIDs** between different extensions.

## ControllerExtension — The Main Class

```java
package com.example.mycontroller;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;

public class MyControllerExtension extends ControllerExtension
{
   protected MyControllerExtension(
         MyControllerExtensionDefinition definition, ControllerHost host)
   {
      super(definition, host);
   }

   @Override
   public void init()
   {
      final ControllerHost host = getHost();
      host.println("My Controller initialized!");

      // All subscriptions, observers, and setup go here.
      // Do NOT send MIDI output from init() — use flush() for that.
   }

   @Override
   public void flush()
   {
      // Called by Bitwig whenever the extension should send MIDI updates
      // to the hardware (LED states, display updates, motorized faders, etc.)
      // This is where ALL outgoing MIDI should be sent.
   }

   @Override
   public void exit()
   {
      // Cleanup when the extension is disabled/unloaded.
      // Reset LEDs, clear displays, etc.
      host.println("My Controller exited.");
   }
}
```

## Build & Deploy

```bash
# Build and deploy to Bitwig Extensions folder
mvn clean install

# Build only (no deploy)
mvn clean package

# The .bwextension file is at:
# target/my-controller.jar  →  copied as  ../Extensions/my-controller.bwextension
```

After building:
1. Open Bitwig Studio
2. Go to **Settings → Controllers**
3. Click **+ Add Controller**
4. Find your extension under the vendor/model you specified
5. Select the MIDI ports

## Checklist for New Projects

- [ ] Generate a unique UUID
- [ ] Create `src/main/resources/META-INF/services/com.bitwig.extension.ExtensionDefinition` with the definition class name
- [ ] Set correct `getRequiredAPIVersion()` matching your `extension-api` dependency version
- [ ] Set `getNumMidiInPorts()` / `getNumMidiOutPorts()` to match your hardware
- [ ] Fill in auto-detection port names if applicable
- [ ] Verify the deploy path matches your OS
- [ ] Run `mvn clean install` and confirm the `.bwextension` appears in the Extensions folder
