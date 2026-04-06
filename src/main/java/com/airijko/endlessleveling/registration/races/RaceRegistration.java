package com.airijko.endlessleveling.registration.races;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.races.RaceDefinition;
import com.airijko.endlessleveling.managers.ExampleFeatureManager;
import com.airijko.endlessleveling.parsing.RaceParser;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans the races directory and registers all enabled JSON race definitions via the Endless Leveling API.
 */
public final class RaceRegistration {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final List<String> registeredRaceIds = new ArrayList<>();

    private RaceRegistration() {
    }

    public static int registerAll(File racesFolder, boolean allowExamples) {
        if (racesFolder == null || !racesFolder.isDirectory()) {
            LOGGER.atWarning().log("Races folder is null or not a directory");
            return 0;
        }

        int count = 0;

        try (Stream<Path> paths = Files.walk(racesFolder.toPath())) {
            List<Path> jsonFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .toList();

            for (Path jsonFile : jsonFiles) {
                if (registerRace(jsonFile, allowExamples)) {
                    count++;
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to scan races folder: %s", e.getMessage());
        }

        LOGGER.atInfo().log("Registered %d addon race(s)", count);
        return count;
    }

    private static boolean registerRace(Path jsonFile, boolean allowExamples) {
        try {
            RaceDefinition definition = RaceParser.parse(jsonFile);
            if (definition == null) {
                return false;
            }

            if (!definition.isEnabled()) {
                LOGGER.atFine().log("Skipping disabled race: %s", definition.getId());
                return false;
            }

            if (!ExampleFeatureManager.get().shouldRegisterContent(
                    jsonFile.getFileName().toString(), definition.getId(), allowExamples)) {
                LOGGER.atFine().log("Skipping example race due to config: %s", definition.getId());
                return false;
            }

            boolean success = EndlessLevelingAPI.get().registerRace(definition, false);
            if (success) {
                registeredRaceIds.add(definition.getId());
                LOGGER.atInfo().log("Registered addon race: %s", definition.getId());
            } else {
                LOGGER.atWarning().log("Failed to register race: %s (may already exist)", definition.getId());
            }
            return success;
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to parse race file %s: %s", jsonFile.getFileName(), e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.atWarning().log("Error registering race from %s: %s", jsonFile.getFileName(), e.getMessage());
            return false;
        }
    }

    public static int unregisterAll() {
        int count = 0;
        for (String raceId : registeredRaceIds) {
            try {
                if (EndlessLevelingAPI.get().unregisterRace(raceId)) {
                    count++;
                    LOGGER.atInfo().log("Unregistered addon race: %s", raceId);
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to unregister race %s: %s", raceId, e.getMessage());
            }
        }
        registeredRaceIds.clear();
        return count;
    }

    public static List<String> getRegisteredRaceIds() {
        return List.copyOf(registeredRaceIds);
    }
}
