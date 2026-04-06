package com.airijko.endlessleveling.parsing;

import com.airijko.endlessleveling.enums.ArchetypePassiveType;
import com.airijko.endlessleveling.enums.DamageLayer;
import com.airijko.endlessleveling.enums.PassiveCategory;
import com.airijko.endlessleveling.enums.PassiveStackingStyle;
import com.airijko.endlessleveling.enums.PassiveTier;
import com.airijko.endlessleveling.enums.SkillAttributeType;
import com.airijko.endlessleveling.passives.util.PassiveDefinitionParser;
import com.airijko.endlessleveling.races.RaceAscensionDefinition;
import com.airijko.endlessleveling.races.RaceAscensionPathLink;
import com.airijko.endlessleveling.races.RaceAscensionRequirements;
import com.airijko.endlessleveling.races.RaceDefinition;
import com.airijko.endlessleveling.races.RacePassiveDefinition;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses race JSON files into RaceDefinition objects.
 */
public final class RaceParser {

    private static final Gson GSON = new Gson();
    private static final Type STRING_OBJECT_MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private RaceParser() {
    }

    public static RaceDefinition parse(Path jsonFile) throws IOException {
        String content = Files.readString(jsonFile);
        Map<String, Object> data = GSON.fromJson(content, STRING_OBJECT_MAP_TYPE);
        if (data == null) {
            throw new IOException("Race file was empty: " + jsonFile.getFileName());
        }
        return buildDefinition(jsonFile, data);
    }

    @SuppressWarnings("unchecked")
    private static RaceDefinition buildDefinition(Path file, Map<String, Object> data) {
        String raceId = deriveId(file, data);
        String displayName = safeString(data.getOrDefault("race_name", raceId));
        String description = safeString(data.get("description"));
        String iconItemId = safeString(data.get("icon"));
        String modelId = safeString(data.get("model"));
        double modelScale = parseDouble(data.getOrDefault("model_scale", 1.0));
        boolean enabled = parseBoolean(data.getOrDefault("enabled", Boolean.TRUE), true);

        EnumMap<SkillAttributeType, Double> attributes = new EnumMap<>(SkillAttributeType.class);
        Object attrNode = data.get("attributes");
        if (attrNode instanceof Map<?, ?> attrMap) {
            for (SkillAttributeType type : SkillAttributeType.values()) {
                Object val = ((Map<String, Object>) attrMap).get(type.getConfigKey());
                if (val != null) {
                    attributes.put(type, parseDouble(val));
                }
            }
        }

        List<Map<String, Object>> passives = parsePassives(data.get("passives"));
        List<RacePassiveDefinition> passiveDefinitions = buildPassiveDefinitions(raceId, passives);
        RaceAscensionDefinition ascension = parseAscensionDefinition(raceId, data.get("ascension"));

        return new RaceDefinition(raceId,
                displayName,
                description,
                iconItemId,
                modelId,
                modelScale,
                enabled,
                attributes,
                passives,
                passiveDefinitions,
                ascension);
    }

    private static String deriveId(Path file, Map<String, Object> data) {
        String idFromJson = safeString(data.get("id"));
        if (idFromJson != null && !idFromJson.isBlank()) {
            return normalizeKey(idFromJson);
        }
        String fileName = file.getFileName().toString();
        if (fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }
        return normalizeKey(fileName);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parsePassives(Object node) {
        List<Map<String, Object>> passives = new ArrayList<>();
        if (!(node instanceof List<?> list)) {
            return passives;
        }
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> passive && ((Map<String, Object>) passive).containsKey("type")) {
                passives.add((Map<String, Object>) passive);
            }
        }
        return passives;
    }

    private static List<RacePassiveDefinition> buildPassiveDefinitions(String raceId, List<Map<String, Object>> passives) {
        List<RacePassiveDefinition> definitions = new ArrayList<>();
        if (passives == null) {
            return definitions;
        }

        for (Map<String, Object> passive : passives) {
            if (passive == null) {
                continue;
            }

            String rawType = safeString(passive.get("type"));
            if (rawType == null) {
                continue;
            }

            ArchetypePassiveType type = ArchetypePassiveType.fromConfigKey(rawType);
            if (type == null) {
                continue;
            }

            double value = parseDouble(passive.get("value"));
            SkillAttributeType attributeType = null;
            if (type == ArchetypePassiveType.INNATE_ATTRIBUTE_GAIN) {
                String attributeKey = safeString(passive.get("attribute"));
                attributeType = SkillAttributeType.fromConfigKey(attributeKey);
                if (attributeType == null) {
                    continue;
                }
            }

            DamageLayer damageLayer = PassiveDefinitionParser.resolveDamageLayer(type, passive);
            String tag = PassiveDefinitionParser.resolveTag(type, passive);
            PassiveStackingStyle stacking = PassiveDefinitionParser.resolveStacking(type, passive);
            PassiveTier tier = PassiveTier.fromConfig(passive.get("tier"), PassiveTier.COMMON);
            PassiveCategory category = PassiveCategory.fromConfig(passive.get("category"), null);
            Map<String, Double> classValues = parseClassValues(passive.get("class_values"));

            definitions.add(new RacePassiveDefinition(type, value, passive,
                    attributeType, damageLayer, tag, category, stacking, tier, classValues));
        }
        return definitions;
    }

    @SuppressWarnings("unchecked")
    private static RaceAscensionDefinition parseAscensionDefinition(String raceId, Object node) {
        if (!(node instanceof Map<?, ?> ascensionNode)) {
            return RaceAscensionDefinition.baseFallback(normalizeKey(raceId));
        }
        Map<String, Object> asc = (Map<String, Object>) ascensionNode;

        String ascensionId = safeString(asc.get("id"));
        if (ascensionId == null) {
            ascensionId = normalizeKey(raceId);
        }
        String stage = safeString(asc.get("stage"));
        String path = safeString(asc.get("path"));
        boolean finalForm = parseBoolean(asc.get("final_form"), false);
        boolean singleRouteOnly = parseBoolean(asc.get("single_route_only"), true);
        if (asc.containsKey("allow_all_routes")) {
            singleRouteOnly = !parseBoolean(asc.get("allow_all_routes"), false);
        }

        RaceAscensionRequirements requirements = parseAscensionRequirements(asc.get("requirements"));
        List<RaceAscensionPathLink> nextPaths = parseAscensionNextPaths(asc.get("next_paths"));

        return new RaceAscensionDefinition(ascensionId, stage, path, finalForm,
                singleRouteOnly, requirements, nextPaths);
    }

    @SuppressWarnings("unchecked")
    private static RaceAscensionRequirements parseAscensionRequirements(Object node) {
        if (!(node instanceof Map<?, ?> reqNode)) {
            return RaceAscensionRequirements.none();
        }
        Map<String, Object> req = (Map<String, Object>) reqNode;
        int requiredPrestige = parseInt(req.get("required_prestige"), 0);
        Map<SkillAttributeType, Integer> minLevels = parseSkillLevelRequirements(req.get("min_skill_levels"));
        Map<SkillAttributeType, Integer> maxLevels = parseSkillLevelRequirements(req.get("max_skill_levels"));
        List<Map<SkillAttributeType, Integer>> minAnySkillLevels = parseMinAnySkillLevels(req.get("min_any_skill_levels"));
        List<String> requiredAugments = parseStringList(req.get("required_augments"));
        List<String> requiredForms = parseStringList(req.get("required_forms"));
        List<String> requiredAnyForms = parseStringList(req.get("required_any_forms"));

        return new RaceAscensionRequirements(requiredPrestige, minLevels, maxLevels,
                minAnySkillLevels, requiredAugments, requiredForms, requiredAnyForms);
    }

    @SuppressWarnings("unchecked")
    private static List<RaceAscensionPathLink> parseAscensionNextPaths(Object node) {
        List<RaceAscensionPathLink> paths = new ArrayList<>();
        if (!(node instanceof List<?> list)) {
            return paths;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> pathMap)) {
                continue;
            }
            Map<String, Object> pm = (Map<String, Object>) pathMap;
            String id = safeString(pm.get("id"));
            String name = safeString(pm.get("name"));
            if (id != null) {
                paths.add(new RaceAscensionPathLink(id, name != null ? name : id));
            }
        }
        return paths;
    }

    @SuppressWarnings("unchecked")
    private static Map<SkillAttributeType, Integer> parseSkillLevelRequirements(Object node) {
        EnumMap<SkillAttributeType, Integer> map = new EnumMap<>(SkillAttributeType.class);
        if (!(node instanceof Map<?, ?> rawMap)) {
            return map;
        }
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            SkillAttributeType type = SkillAttributeType.fromConfigKey(entry.getKey().toString());
            if (type != null) {
                map.put(type, parseInt(entry.getValue(), 0));
            }
        }
        return map;
    }

    private static List<Map<SkillAttributeType, Integer>> parseMinAnySkillLevels(Object node) {
        List<Map<SkillAttributeType, Integer>> result = new ArrayList<>();
        if (!(node instanceof List<?> list)) {
            return result;
        }
        for (Object entry : list) {
            Map<SkillAttributeType, Integer> parsed = parseSkillLevelRequirements(entry);
            if (!parsed.isEmpty()) {
                result.add(parsed);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> parseClassValues(Object node) {
        if (!(node instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            result.put(entry.getKey().toString(), parseDouble(entry.getValue()));
        }
        return result.isEmpty() ? null : result;
    }

    private static List<String> parseStringList(Object node) {
        List<String> result = new ArrayList<>();
        if (!(node instanceof List<?> list)) {
            return result;
        }
        for (Object entry : list) {
            if (entry != null) {
                String text = entry.toString().trim();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    private static String safeString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean parseBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return fallback;
    }

    private static double parseDouble(Object value) {
        return parseDouble(value, 0.0D);
    }

    private static double parseDouble(Object value, double fallback) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
