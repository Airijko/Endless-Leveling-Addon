package com.airijko.endlessleveling.managers;

/**
 * Version source of truth for addon-managed files and bundled example content.
 */
public final class AddonVersionRegistry {

    private AddonVersionRegistry() {
    }

    public static final int CONFIG_JSON_VERSION = 1;

    public static final int BUILTIN_AUGMENTS_VERSION = 1;
    public static final int BUILTIN_CLASSES_VERSION = 2;
    public static final int BUILTIN_RACES_VERSION = 2;
    public static final int BUILTIN_PASSIVES_VERSION = 1;

    public static final String AUGMENTS_VERSION_FILE = "augments.version";
    public static final String CLASSES_VERSION_FILE = "classes.version";
    public static final String RACES_VERSION_FILE = "races.version";
    public static final String PASSIVES_VERSION_FILE = "passives.version";
}
