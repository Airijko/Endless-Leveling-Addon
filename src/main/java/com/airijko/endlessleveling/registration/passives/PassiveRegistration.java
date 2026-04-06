package com.airijko.endlessleveling.registration.passives;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.enums.ArchetypePassiveType;
import com.airijko.endlessleveling.enums.DamageLayer;
import com.airijko.endlessleveling.enums.PassiveStackingStyle;
import com.airijko.endlessleveling.enums.PassiveTier;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.managers.ExampleFeatureManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Scans the passives directory and registers all enabled JSON passive definitions via the Endless Leveling API.
 */
public final class PassiveRegistration {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final Gson GSON = new Gson();
    private static final Type STRING_OBJECT_MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final List<YamlPassiveSource> registeredSources = new ArrayList<>();

    private PassiveRegistration() {
    }

    public static int registerAll(File passivesFolder, boolean allowExamples) {
        if (passivesFolder == null || !passivesFolder.isDirectory()) {
            LOGGER.atWarning().log("Passives folder is null or not a directory");
            return 0;
        }

        int count = 0;

        try (Stream<Path> paths = Files.walk(passivesFolder.toPath())) {
            List<Path> jsonFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .toList();

            for (Path jsonFile : jsonFiles) {
                if (registerPassive(jsonFile, allowExamples)) {
                    count++;
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to scan passives folder: %s", e.getMessage());
        }

        LOGGER.atInfo().log("Registered %d addon passive(s)", count);
        return count;
    }

    @SuppressWarnings("unchecked")
    private static boolean registerPassive(Path jsonFile, boolean allowExamples) {
        try {
            String content = Files.readString(jsonFile);
            Map<String, Object> root = GSON.fromJson(content, STRING_OBJECT_MAP_TYPE);
            if (root == null) {
                return false;
            }

            // Support both root-level and nested "passive" structure
            Map<String, Object> passive = asMap(root.get("passive"));
            if (passive.isEmpty()) {
                passive = root;
            }

            String id = stringValue(passive.get("id"), null);
            if (id == null) {
                id = stripExtension(jsonFile.getFileName().toString());
            }

            boolean enabled = booleanValue(passive.get("enabled"), true);
            if (!enabled) {
                LOGGER.atFine().log("Skipping disabled passive: %s", id);
                return false;
            }

            if (!ExampleFeatureManager.get().shouldRegisterContent(
                    jsonFile.getFileName().toString(), id, allowExamples)) {
                LOGGER.atFine().log("Skipping example passive due to config: %s", id);
                return false;
            }

            String typeStr = stringValue(passive.get("type"), null);
            ArchetypePassiveType type = typeStr != null ? ArchetypePassiveType.fromConfigKey(typeStr) : null;
            if (type == null) {
                LOGGER.atWarning().log("Passive %s has invalid or missing type", id);
                return false;
            }

            double value = doubleValue(passive.get("value"), 0.0D);

            String attributeStr = stringValue(passive.get("attribute"), null);
            SkillAttributeType attributeType = attributeStr != null
                    ? SkillAttributeType.fromConfigKey(attributeStr) : null;

            String layerStr = stringValue(passive.get("layer"),
                    stringValue(passive.get("damage_layer"), null));
            DamageLayer damageLayer = layerStr != null ? DamageLayer.fromConfig(layerStr, null) : null;

            String stackingStr = stringValue(passive.get("stacking"),
                    stringValue(passive.get("stacking_style"), null));
            PassiveStackingStyle stackingStyle = stackingStr != null
                    ? PassiveStackingStyle.fromConfig(stackingStr, null) : null;

            PassiveTier tier = PassiveTier.fromConfig(passive.get("tier"), PassiveTier.COMMON);

            // Collect remaining properties
            Map<String, Object> properties = new LinkedHashMap<>(passive);
            properties.remove("id");
            properties.remove("enabled");
            properties.remove("type");
            properties.remove("value");
            properties.remove("attribute");
            properties.remove("layer");
            properties.remove("damage_layer");
            properties.remove("stacking");
            properties.remove("stacking_style");
            properties.remove("tier");

            YamlPassiveSource source = new YamlPassiveSource(
                    id, type, value, properties,
                    attributeType, damageLayer, stackingStyle, tier);

            boolean success = EndlessLevelingAPI.get().registerArchetypePassiveSource(source);
            if (success) {
                registeredSources.add(source);
                LOGGER.atInfo().log("Registered addon passive: %s (%s)", id, type);
            } else {
                LOGGER.atWarning().log("Failed to register passive: %s", id);
            }
            return success;
        } catch (Exception e) {
            LOGGER.atWarning().log("Error registering passive from %s: %s", jsonFile.getFileName(), e.getMessage());
            return false;
        }
    }

    public static int unregisterAll() {
        int count = 0;
        for (YamlPassiveSource source : registeredSources) {
            try {
                if (EndlessLevelingAPI.get().unregisterArchetypePassiveSource(source)) {
                    count++;
                    LOGGER.atInfo().log("Unregistered addon passive: %s", source.getId());
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to unregister passive %s: %s", source.getId(), e.getMessage());
            }
        }
        registeredSources.clear();
        return count;
    }

    public static List<String> getRegisteredPassiveIds() {
        return registeredSources.stream().map(YamlPassiveSource::getId).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    private static String stringValue(Object value, String fallback) {
        if (value instanceof String str && !str.isBlank()) {
            return str.trim();
        }
        return fallback;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str && !str.isBlank()) {
            return Boolean.parseBoolean(str.trim());
        }
        return fallback;
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Double.parseDouble(str.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static String stripExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "passive";
        }
        int idx = fileName.lastIndexOf('.');
        return idx <= 0 ? fileName : fileName.substring(0, idx);
    }
}
