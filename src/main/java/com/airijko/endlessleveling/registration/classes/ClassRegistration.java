package com.airijko.endlessleveling.registration.classes;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.classes.CharacterClassDefinition;
import com.airijko.endlessleveling.managers.ExampleFeatureManager;
import com.airijko.endlessleveling.parsing.ClassParser;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans the classes directory and registers all enabled JSON class definitions via the Endless Leveling API.
 */
public final class ClassRegistration {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final List<String> registeredClassIds = new ArrayList<>();

    private ClassRegistration() {
    }

    public static int registerAll(File classesFolder, boolean allowExamples) {
        if (classesFolder == null || !classesFolder.isDirectory()) {
            LOGGER.atWarning().log("Classes folder is null or not a directory");
            return 0;
        }

        int count = 0;

        try (Stream<Path> paths = Files.walk(classesFolder.toPath())) {
            List<Path> jsonFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .toList();

            for (Path jsonFile : jsonFiles) {
                if (registerClass(jsonFile, allowExamples)) {
                    count++;
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to scan classes folder: %s", e.getMessage());
        }

        LOGGER.atInfo().log("Registered %d addon class(es)", count);
        return count;
    }

    private static boolean registerClass(Path jsonFile, boolean allowExamples) {
        try {
            CharacterClassDefinition definition = ClassParser.parse(jsonFile);
            if (definition == null) {
                return false;
            }

            if (!definition.isEnabled()) {
                LOGGER.atFine().log("Skipping disabled class: %s", definition.getId());
                return false;
            }

            if (!ExampleFeatureManager.get().shouldRegisterContent(
                    jsonFile.getFileName().toString(), definition.getId(), allowExamples)) {
                LOGGER.atFine().log("Skipping example class due to config: %s", definition.getId());
                return false;
            }

            boolean success = EndlessLevelingAPI.get().registerClass(definition, false);
            if (success) {
                registeredClassIds.add(definition.getId());
                LOGGER.atInfo().log("Registered addon class: %s", definition.getId());
            } else {
                LOGGER.atWarning().log("Failed to register class: %s (may already exist)", definition.getId());
            }
            return success;
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to parse class file %s: %s", jsonFile.getFileName(), e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.atWarning().log("Error registering class from %s: %s", jsonFile.getFileName(), e.getMessage());
            return false;
        }
    }

    public static int unregisterAll() {
        int count = 0;
        for (String classId : registeredClassIds) {
            try {
                if (EndlessLevelingAPI.get().unregisterClass(classId)) {
                    count++;
                    LOGGER.atInfo().log("Unregistered addon class: %s", classId);
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to unregister class %s: %s", classId, e.getMessage());
            }
        }
        registeredClassIds.clear();
        return count;
    }

    public static List<String> getRegisteredClassIds() {
        return List.copyOf(registeredClassIds);
    }
}
