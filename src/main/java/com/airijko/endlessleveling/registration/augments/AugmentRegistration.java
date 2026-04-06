package com.airijko.endlessleveling.registration.augments;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.augments.Augment;
import com.airijko.endlessleveling.augments.AugmentDefinition;
import com.airijko.endlessleveling.enums.PassiveCategory;
import com.airijko.endlessleveling.enums.PassiveTier;
import com.airijko.endlessleveling.managers.ExampleFeatureManager;
import com.airijko.endlessleveling.registration.augments.examples.ConquerorExampleAugment;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Scans the augments directory and registers all enabled JSON augment definitions via the Endless Leveling API.
 * Augments default to data-only registration, with optional factory-backed Java behavior for examples.
 */
public final class AugmentRegistration {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final Gson GSON = new Gson();
    private static final Type STRING_OBJECT_MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private static final Map<String, Function<AugmentDefinition, Augment>> EXAMPLE_FACTORIES = Map.of(
            ConquerorExampleAugment.ID, ConquerorExampleAugment::new);

    private static final List<String> registeredAugmentIds = new ArrayList<>();

    private AugmentRegistration() {
    }

    public static int registerAll(File augmentsFolder, boolean allowExamples) {
        if (augmentsFolder == null || !augmentsFolder.isDirectory()) {
            LOGGER.atWarning().log("Augments folder is null or not a directory");
            return 0;
        }

        int count = 0;

        try (Stream<Path> paths = Files.walk(augmentsFolder.toPath())) {
            List<Path> jsonFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .toList();

            for (Path jsonFile : jsonFiles) {
                if (registerAugment(jsonFile, allowExamples)) {
                    count++;
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to scan augments folder: %s", e.getMessage());
        }

        LOGGER.atInfo().log("Registered %d addon augment(s)", count);
        return count;
    }

    @SuppressWarnings("unchecked")
    private static boolean registerAugment(Path jsonFile, boolean allowExamples) {
        try {
            String content = Files.readString(jsonFile);
            Map<String, Object> root = GSON.fromJson(content, STRING_OBJECT_MAP_TYPE);
            if (root == null) {
                return false;
            }

            boolean enabled = booleanVal(root.get("enabled"), true);
            if (!enabled) {
                LOGGER.atFine().log("Skipping disabled augment: %s", jsonFile.getFileName());
                return false;
            }

            AugmentDefinition definition = parseDefinition(jsonFile, root);
            if (definition == null) {
                return false;
            }

            if (!ExampleFeatureManager.get().shouldRegisterContent(
                    jsonFile.getFileName().toString(), definition.getId(), allowExamples)) {
                LOGGER.atFine().log("Skipping example augment due to config: %s", definition.getId());
                return false;
            }

            String augmentId = normalizeId(definition.getId());
            Function<AugmentDefinition, Augment> factory = augmentId == null
                    ? null
                    : EXAMPLE_FACTORIES.get(augmentId);

            boolean success = factory == null
                    ? EndlessLevelingAPI.get().registerAugment(definition)
                    : EndlessLevelingAPI.get().registerAugment(definition, factory);

            if (success) {
                registeredAugmentIds.add(definition.getId());
                if (factory == null) {
                    LOGGER.atInfo().log("Registered addon augment: %s", definition.getId());
                } else {
                    LOGGER.atInfo().log("Registered addon augment with backend factory: %s", definition.getId());
                }
            } else {
                LOGGER.atWarning().log("Failed to register augment: %s (may already exist)", definition.getId());
            }
            return success;
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to parse augment file %s: %s", jsonFile.getFileName(), e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.atWarning().log("Error registering augment from %s: %s", jsonFile.getFileName(), e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static AugmentDefinition parseDefinition(Path file, Map<String, Object> root) {
        String id = stringVal(root.get("id"), stripExtension(file.getFileName().toString()));
        String name = stringVal(root.get("name"), id);
        String description = stringVal(root.get("description"), "");
        PassiveTier tier = PassiveTier.fromConfig(root.get("tier"), PassiveTier.COMMON);
        PassiveCategory category = PassiveCategory.fromConfig(root.get("category"), null);
        boolean stackable = booleanVal(root.get("stackable"), false);
        boolean mobCompatible = booleanVal(root.get("mob_compatible"), false);

        Object passivesNode = root.getOrDefault("passives", Collections.emptyMap());
        Map<String, Object> passives = passivesNode instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Collections.emptyMap();

        List<AugmentDefinition.UiSection> uiSections = parseUiSections(root);
        return new AugmentDefinition(id, name, tier, category, stackable, description, passives, uiSections, mobCompatible);
    }

    @SuppressWarnings("unchecked")
    private static List<AugmentDefinition.UiSection> parseUiSections(Map<String, Object> root) {
        List<AugmentDefinition.UiSection> sections = new ArrayList<>();
        Object uiNode = root.get("ui");
        if (!(uiNode instanceof Map<?, ?> uiMapRaw)) {
            return sections;
        }
        Map<String, Object> uiMap = (Map<String, Object>) uiMapRaw;
        Object sectionsNode = uiMap.get("sections");
        if (!(sectionsNode instanceof List<?> list)) {
            return sections;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> sectionRaw)) {
                continue;
            }
            Map<String, Object> section = (Map<String, Object>) sectionRaw;
            String title = stringVal(section.get("title"), "");
            String body = stringVal(section.get("body"), "");
            String color = stringVal(section.get("color"), "");
            sections.add(new AugmentDefinition.UiSection(title, body, color));
        }
        return sections;
    }

    private static String stringVal(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }

    private static boolean booleanVal(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return fallback;
    }

    private static String stripExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "augment";
        }
        int idx = fileName.lastIndexOf('.');
        return idx <= 0 ? fileName : fileName.substring(0, idx);
    }

    private static String normalizeId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        return rawId.trim().toLowerCase(Locale.ROOT);
    }

    public static int unregisterAll() {
        int count = 0;
        for (String augmentId : registeredAugmentIds) {
            try {
                if (EndlessLevelingAPI.get().unregisterAugment(augmentId)) {
                    count++;
                    LOGGER.atInfo().log("Unregistered addon augment: %s", augmentId);
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to unregister augment %s: %s", augmentId, e.getMessage());
            }
        }
        registeredAugmentIds.clear();
        return count;
    }

    public static List<String> getRegisteredAugmentIds() {
        return List.copyOf(registeredAugmentIds);
    }
}
