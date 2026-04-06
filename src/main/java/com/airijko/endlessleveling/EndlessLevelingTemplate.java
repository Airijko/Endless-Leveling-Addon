package com.airijko.endlessleveling;

import com.airijko.endlessleveling.managers.AddonFilesManager;
import com.airijko.endlessleveling.managers.ExampleFeatureManager;
import com.airijko.endlessleveling.registration.augments.AugmentRegistration;
import com.airijko.endlessleveling.registration.classes.ClassRegistration;
import com.airijko.endlessleveling.registration.passives.PassiveRegistration;
import com.airijko.endlessleveling.registration.races.RaceRegistration;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

public class EndlessLevelingTemplate extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    private AddonFilesManager filesManager;

    public EndlessLevelingTemplate(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.filesManager = new AddonFilesManager(this);

        if (this.filesManager.shouldMergeRacesWithCore()) {
            RaceRegistration.registerAll(this.filesManager.getRacesFolder(),
                    this.filesManager.shouldEnableExampleRaces());
        }
        if (this.filesManager.shouldMergeClassesWithCore()) {
            ClassRegistration.registerAll(this.filesManager.getClassesFolder(),
                    this.filesManager.shouldEnableExampleClasses());
        }
        if (this.filesManager.shouldMergeAugmentsWithCore()) {
            AugmentRegistration.registerAll(this.filesManager.getAugmentsFolder(),
                    this.filesManager.shouldEnableExampleAugments());
        }
        if (this.filesManager.shouldMergePassivesWithCore()) {
            PassiveRegistration.registerAll(this.filesManager.getPassivesFolder(),
                    this.filesManager.shouldEnableExamplePassives());
        }

        ExampleFeatureManager.get().configure(
                this.filesManager.shouldEnableExampleCommand(),
                this.filesManager.shouldEnableExampleEvents());
        ExampleFeatureManager.get().registerExamples(this);
    }

    @Override
    protected void shutdown() {
        RaceRegistration.unregisterAll();
        ClassRegistration.unregisterAll();
        AugmentRegistration.unregisterAll();
        PassiveRegistration.unregisterAll();
    }
}
