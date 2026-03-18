package com.airijko.endlessleveling.compatibility;

/**
 * Optional bridge for Endless Leveling API.
 */
public final class EndlessLevelingCompatibility {

	private static final String API_CLASS = "com.airijko.endlessleveling.api.EndlessLevelingAPI";

	private EndlessLevelingCompatibility() {
	}

    public static boolean isAvailable() {
        return getApiInstance() != null;
    }

    public static Object getApiInstance() {
        try {
            return Class.forName(API_CLASS).getMethod("get").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
