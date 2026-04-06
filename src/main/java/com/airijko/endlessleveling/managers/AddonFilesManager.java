package com.airijko.endlessleveling.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Minimal file bootstrap matching Endless Leveling's resource-first startup flow.
 * Uses JSON for all configuration and content files.
 */
public final class AddonFilesManager {

    private static final String PLUGIN_FOLDER_NAME = "EndlessLevelingTemplate";
    private static final DateTimeFormatter ARCHIVE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern MANIFEST_VERSION_PATTERN = Pattern.compile("\"Version\"\\s*:\\s*\"([^\"]+)\"");
    private static final Type STRING_OBJECT_MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private final JavaPlugin plugin;
    private final Gson gson = new Gson();
    private final File pluginFolder;
    private final File racesFolder;
    private final File classesFolder;
    private final File augmentsFolder;
    private final File passivesFolder;
    private final File configFile;
    private final Object archiveLock = new Object();

    private AddonContentOptions contentOptions;
    private Path currentArchiveSession;

    public AddonFilesManager(JavaPlugin plugin) {
        this.plugin = plugin;

        this.pluginFolder = PluginManager.MODS_PATH.resolve(PLUGIN_FOLDER_NAME).toFile();
        this.racesFolder = new File(pluginFolder, "races");
        this.classesFolder = new File(pluginFolder, "classes");
        this.augmentsFolder = new File(pluginFolder, "augments");
        this.passivesFolder = new File(pluginFolder, "passives");
        this.configFile = new File(pluginFolder, "config.json");

        initialize();
    }

    private void initialize() {
        createFolders();
        initJsonFile("config.json");
        this.contentOptions = loadContentOptions();
        syncConfigIfNeeded();

        syncDirectoryIfNeeded("races", racesFolder,
                AddonVersionRegistry.RACES_VERSION_FILE,
                AddonVersionRegistry.BUILTIN_RACES_VERSION,
                contentOptions.mergeRacesWithCore,
                contentOptions.exampleRacesEnabled);

        syncDirectoryIfNeeded("classes", classesFolder,
                AddonVersionRegistry.CLASSES_VERSION_FILE,
                AddonVersionRegistry.BUILTIN_CLASSES_VERSION,
                contentOptions.mergeClassesWithCore,
                contentOptions.exampleClassesEnabled);

        syncDirectoryIfNeeded("augments", augmentsFolder,
                AddonVersionRegistry.AUGMENTS_VERSION_FILE,
                AddonVersionRegistry.BUILTIN_AUGMENTS_VERSION,
                contentOptions.mergeAugmentsWithCore,
                contentOptions.exampleAugmentsEnabled);

        syncDirectoryIfNeeded("passives", passivesFolder,
                AddonVersionRegistry.PASSIVES_VERSION_FILE,
                AddonVersionRegistry.BUILTIN_PASSIVES_VERSION,
                contentOptions.mergePassivesWithCore,
                contentOptions.examplePassivesEnabled);
    }

    private void syncConfigIfNeeded() {
        int storedVersion = readConfigVersion(configFile);
        int targetVersion = AddonVersionRegistry.CONFIG_JSON_VERSION;
        if (storedVersion == targetVersion) {
            return;
        }

        AddonContentOptions migratedOptions = contentOptions == null
                ? AddonContentOptions.defaults()
                : contentOptions;

        archiveFileIfExists(configFile, "config.json", "config_version:" + storedVersion);
        writeNormalizedConfig(migratedOptions, targetVersion);

        LOGGER.atInfo().log("Migrated config.json to version %d", targetVersion);
        this.contentOptions = loadContentOptions();
    }

    private void syncDirectoryIfNeeded(String resourceRoot,
            File destination,
            String versionFileName,
            int targetVersion,
            boolean mergeEnabled,
            boolean allowSeed) {
        if (!mergeEnabled || destination == null) {
            return;
        }

        int storedVersion = readDirectoryVersion(destination, versionFileName);
        if (storedVersion >= 0 && storedVersion < targetVersion) {
            exportResourceDirectory(resourceRoot, destination, false);
            writeDirectoryVersion(destination, versionFileName, targetVersion);
            LOGGER.atInfo().log("Migrated %s from version %d to %d (non-destructive)",
                    resourceRoot, storedVersion, targetVersion);
            return;
        }

        if (!allowSeed) {
            return;
        }

        if (seedResourceDirectoryIfEmpty(resourceRoot, destination)) {
            writeDirectoryVersion(destination, versionFileName, targetVersion);
        }
    }

    @SuppressWarnings("unchecked")
    private AddonContentOptions loadContentOptions() {
        AddonContentOptions defaults = AddonContentOptions.defaults();
        if (!configFile.isFile()) {
            return defaults;
        }

        try {
            String json = Files.readString(configFile.toPath());
            Map<String, Object> root = gson.fromJson(json, STRING_OBJECT_MAP_TYPE);
            if (root == null) {
                return defaults;
            }

            Map<String, Object> merge = asMap(root.get("core_content_merge"));
            Map<String, Object> examples = asMap(root.get("examples"));

            boolean mergeRaces = readBoolean(merge.get("races"), true);
            boolean mergeClasses = readBoolean(merge.get("classes"), true);
            boolean mergeAugments = readBoolean(merge.get("augments"), true);
            boolean mergePassives = readBoolean(merge.get("passives"), true);

            boolean enableExampleRaces = readBoolean(examples.get("races"), true);
            boolean enableExampleClasses = readBoolean(examples.get("classes"), true);
            boolean enableExampleAugments = readBoolean(examples.get("augments"), true);
            boolean enableExamplePassives = readBoolean(examples.get("passives"), true);
            boolean enableExampleCommand = readBoolean(examples.get("command"), true);
            boolean enableExampleEvents = readBoolean(examples.get("events"), true);

            return new AddonContentOptions(
                    mergeRaces, mergeClasses, mergeAugments, mergePassives,
                    enableExampleRaces, enableExampleClasses, enableExampleAugments, enableExamplePassives,
                    enableExampleCommand, enableExampleEvents);
        } catch (IOException ignored) {
            return defaults;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    private static boolean readBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str && !str.isBlank()) {
            return Boolean.parseBoolean(str.trim());
        }
        return fallback;
    }

    private void createFolders() {
        try {
            Files.createDirectories(pluginFolder.toPath());
            Files.createDirectories(racesFolder.toPath());
            Files.createDirectories(classesFolder.toPath());
            Files.createDirectories(augmentsFolder.toPath());
            Files.createDirectories(passivesFolder.toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create addon folders", exception);
        }
    }

    public File initJsonFile(String resourceName) {
        File jsonFile = new File(pluginFolder, resourceName);
        if (jsonFile.exists()) {
            return jsonFile;
        }

        try (InputStream resourceStream = plugin.getClassLoader().getResourceAsStream(resourceName)) {
            if (resourceStream == null) {
                LOGGER.atWarning().log("Resource %s not found in addon JAR", resourceName);
                return jsonFile;
            }
            Files.copy(resourceStream, jsonFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.atInfo().log("Created %s at %s", resourceName, jsonFile.getAbsolutePath());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create JSON file: " + resourceName, exception);
        }
        return jsonFile;
    }

    private int readConfigVersion(File jsonFile) {
        if (jsonFile == null || !jsonFile.exists()) {
            return -1;
        }
        try {
            String json = Files.readString(jsonFile.toPath());
            Map<String, Object> root = gson.fromJson(json, STRING_OBJECT_MAP_TYPE);
            if (root != null && root.containsKey("config_version")) {
                return ((Number) root.get("config_version")).intValue();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private void writeNormalizedConfig(AddonContentOptions options, int targetVersion) {
        String json = gson.toJson(Map.of(
                "core_content_merge", Map.of(
                        "races", options.mergeRacesWithCore,
                        "classes", options.mergeClassesWithCore,
                        "augments", options.mergeAugmentsWithCore,
                        "passives", options.mergePassivesWithCore
                ),
                "examples", Map.of(
                        "races", options.exampleRacesEnabled,
                        "classes", options.exampleClassesEnabled,
                        "augments", options.exampleAugmentsEnabled,
                        "passives", options.examplePassivesEnabled,
                        "command", options.exampleCommandEnabled,
                        "events", options.exampleEventsEnabled
                ),
                "config_version", targetVersion
        ));

        try {
            Files.writeString(configFile.toPath(), json,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write config.json", exception);
        }
    }

    public Path archiveFileIfExists(File sourceFile, String archiveRelativePath, String priorVersionTag) {
        if (sourceFile == null || !sourceFile.exists()) {
            return null;
        }
        synchronized (archiveLock) {
            Path archiveRoot = getOrCreateArchiveSession();
            Path targetPath = archiveRelativePath == null || archiveRelativePath.isBlank()
                    ? archiveRoot.resolve(sourceFile.getName())
                    : archiveRoot.resolve(archiveRelativePath);
            try {
                Path parent = targetPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.atInfo().log("Archived %s to %s", sourceFile.toPath(), targetPath);
                return targetPath;
            } catch (IOException e) {
                LOGGER.atWarning().log("Failed to archive %s: %s", sourceFile.toPath(), e.getMessage());
                return null;
            }
        }
    }

    private Path getOrCreateArchiveSession() {
        if (currentArchiveSession != null) {
            return currentArchiveSession;
        }
        String timestamp = LocalDateTime.now().format(ARCHIVE_TIMESTAMP_FORMAT);
        String pluginVersion = sanitizeForPath(resolvePluginVersion());
        Path sessionPath = pluginFolder.toPath().resolve("old").resolve(timestamp + "_v" + pluginVersion);
        try {
            Files.createDirectories(sessionPath);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create backup folder", e);
        }
        currentArchiveSession = sessionPath;
        return currentArchiveSession;
    }

    private String resolvePluginVersion() {
        try (InputStream in = plugin.getClassLoader().getResourceAsStream("manifest.json")) {
            if (in != null) {
                String json = new String(in.readAllBytes());
                Matcher matcher = MANIFEST_VERSION_PATTERN.matcher(json);
                if (matcher.find()) {
                    return matcher.group(1).trim();
                }
            }
        } catch (IOException ignored) {
        }
        return "unknown";
    }

    private String sanitizeForPath(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public void exportResourceDirectory(String resourceRoot, File destination, boolean overwriteExisting) {
        try {
            Files.createDirectories(destination.toPath());
            CodeSource codeSource = plugin.getClass().getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                LOGGER.atWarning().log("Unable to locate code source while exporting %s", resourceRoot);
                return;
            }

            Path sourcePath = Paths.get(codeSource.getLocation().toURI());
            if (Files.isDirectory(sourcePath)) {
                Path resourcePath = sourcePath.resolve(resourceRoot);
                if (!Files.exists(resourcePath)) {
                    return;
                }
                copyDirectory(resourcePath, destination.toPath(), overwriteExisting);
                return;
            }

            try (InputStream fileInput = Files.newInputStream(sourcePath);
                    JarInputStream jarStream = new JarInputStream(fileInput)) {
                String prefix = resourceRoot.endsWith("/") ? resourceRoot : resourceRoot + "/";
                JarEntry entry;
                while ((entry = jarStream.getNextJarEntry()) != null) {
                    try {
                        if (entry.isDirectory()) {
                            continue;
                        }
                        String name = entry.getName();
                        if (!name.startsWith(prefix)) {
                            continue;
                        }
                        Path relativePath = Paths.get(name.substring(prefix.length()));
                        Path targetPath = destination.toPath().resolve(relativePath.toString());
                        if (!overwriteExisting && Files.exists(targetPath)) {
                            continue;
                        }
                        Path parent = targetPath.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        Files.copy(jarStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    } finally {
                        jarStream.closeEntry();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to export resource directory %s: %s", resourceRoot, e.getMessage());
        }
    }

    private boolean seedResourceDirectoryIfEmpty(String resourceRoot, File destination) {
        if (destination == null || hasJsonFiles(destination.toPath())) {
            return false;
        }
        exportResourceDirectory(resourceRoot, destination, false);
        return hasJsonFiles(destination.toPath());
    }

    private boolean hasJsonFiles(Path folder) {
        if (folder == null || !Files.exists(folder)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile)
                    .anyMatch(path -> path.toString().toLowerCase().endsWith(".json"));
        } catch (IOException e) {
            return false;
        }
    }

    private void copyDirectory(Path source, Path destination, boolean overwriteExisting) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(source)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                Path relative = source.relativize(path);
                Path target = destination.resolve(relative.toString());
                if (!overwriteExisting && Files.exists(target)) {
                    return;
                }
                try {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    LOGGER.atWarning().log("Failed to copy resource %s: %s", relative, e.getMessage());
                }
            });
        }
    }

    private int readDirectoryVersion(File folder, String versionFileName) {
        Path versionPath = folder.toPath().resolve(versionFileName);
        if (!Files.exists(versionPath)) {
            return -1;
        }
        try {
            return Integer.parseInt(Files.readString(versionPath).trim());
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void writeDirectoryVersion(File folder, String versionFileName, int version) {
        Path versionPath = folder.toPath().resolve(versionFileName);
        try {
            Files.writeString(versionPath, Integer.toString(version));
        } catch (IOException e) {
            LOGGER.atWarning().log("Failed to write %s: %s", versionFileName, e.getMessage());
        }
    }

    // ── Getters ──

    public File getPluginFolder() { return pluginFolder; }
    public File getConfigFile() { return configFile; }
    public File getRacesFolder() { return racesFolder; }
    public File getClassesFolder() { return classesFolder; }
    public File getAugmentsFolder() { return augmentsFolder; }
    public File getPassivesFolder() { return passivesFolder; }

    public boolean shouldMergeRacesWithCore() { return contentOptions == null || contentOptions.mergeRacesWithCore; }
    public boolean shouldMergeClassesWithCore() { return contentOptions == null || contentOptions.mergeClassesWithCore; }
    public boolean shouldMergeAugmentsWithCore() { return contentOptions == null || contentOptions.mergeAugmentsWithCore; }
    public boolean shouldMergePassivesWithCore() { return contentOptions == null || contentOptions.mergePassivesWithCore; }
    public boolean shouldEnableExampleRaces() { return contentOptions == null || contentOptions.exampleRacesEnabled; }
    public boolean shouldEnableExampleClasses() { return contentOptions == null || contentOptions.exampleClassesEnabled; }
    public boolean shouldEnableExampleAugments() { return contentOptions == null || contentOptions.exampleAugmentsEnabled; }
    public boolean shouldEnableExamplePassives() { return contentOptions == null || contentOptions.examplePassivesEnabled; }
    public boolean shouldEnableExampleCommand() { return contentOptions == null || contentOptions.exampleCommandEnabled; }
    public boolean shouldEnableExampleEvents() { return contentOptions == null || contentOptions.exampleEventsEnabled; }

    // ── Config Model ──

    private static final class AddonContentOptions {
        final boolean mergeRacesWithCore;
        final boolean mergeClassesWithCore;
        final boolean mergeAugmentsWithCore;
        final boolean mergePassivesWithCore;
        final boolean exampleRacesEnabled;
        final boolean exampleClassesEnabled;
        final boolean exampleAugmentsEnabled;
        final boolean examplePassivesEnabled;
        final boolean exampleCommandEnabled;
        final boolean exampleEventsEnabled;

        AddonContentOptions(boolean mergeRacesWithCore,
                boolean mergeClassesWithCore,
                boolean mergeAugmentsWithCore,
                boolean mergePassivesWithCore,
                boolean exampleRacesEnabled,
                boolean exampleClassesEnabled,
                boolean exampleAugmentsEnabled,
                boolean examplePassivesEnabled,
                boolean exampleCommandEnabled,
                boolean exampleEventsEnabled) {
            this.mergeRacesWithCore = mergeRacesWithCore;
            this.mergeClassesWithCore = mergeClassesWithCore;
            this.mergeAugmentsWithCore = mergeAugmentsWithCore;
            this.mergePassivesWithCore = mergePassivesWithCore;
            this.exampleRacesEnabled = exampleRacesEnabled;
            this.exampleClassesEnabled = exampleClassesEnabled;
            this.exampleAugmentsEnabled = exampleAugmentsEnabled;
            this.examplePassivesEnabled = examplePassivesEnabled;
            this.exampleCommandEnabled = exampleCommandEnabled;
            this.exampleEventsEnabled = exampleEventsEnabled;
        }

        static AddonContentOptions defaults() {
            return new AddonContentOptions(true, true, true, true, true, true, true, true, true, true);
        }
    }
}
