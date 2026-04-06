# Endless Leveling Template

A template addon for [Endless Leveling](https://www.curseforge.com/hytale/mods/endless-leveling) demonstrating how to register custom **classes**, **races**, **augments**, and **passives** using JSON definitions.

## Getting Started

1. **Rename** the project — update `settings.gradle`, `build.gradle`, `manifest.json`, and the Java package from `com.example.endlessleveling` to your own.
2. **Add content** — drop `.json` files into `src/main/resources/races/`, `classes/`, `augments/`, or `passives/`.
3. **Build** — `./gradlew build` (or `./gradlew package`). The output jar goes into `build/libs/`.
4. **Deploy** — copy the jar to your Hytale server's `mods/` directory alongside `EndlessLeveling`.

## Project Structure

```
src/main/
├── java/com/example/endlessleveling/
│   ├── EndlessLevelingTemplate.java          # Plugin entry point
│   ├── commands/
│   │   └── ExampleCommand.java               # Example /example command
│   ├── events/
│   │   └── ExampleEvent.java                 # Example player-ready hook
│   ├── managers/
│   │   ├── AddonFilesManager.java            # File bootstrap & config loading
│   │   ├── AddonVersionRegistry.java         # Content version tracking
│   │   └── ExampleFeatureManager.java        # Toggle example features
│   ├── parsing/
│   │   ├── ClassParser.java                  # JSON → CharacterClassDefinition
│   │   └── RaceParser.java                   # JSON → RaceDefinition
│   └── registration/
│       ├── augments/
│       │   ├── AugmentRegistration.java      # Scans & registers augment JSONs
│       │   └── examples/
│       │       └── ConquerorExampleAugment.java  # Factory-backed augment example
│       ├── classes/
│       │   └── ClassRegistration.java        # Scans & registers class JSONs
│       ├── passives/
│       │   ├── PassiveRegistration.java      # Scans & registers passive JSONs
│       │   └── YamlPassiveSource.java        # Passive → ArchetypePassiveSource bridge
│       └── races/
│           └── RaceRegistration.java         # Scans & registers race JSONs
└── resources/
    ├── manifest.json                         # Hytale mod descriptor
    ├── config.json                           # Addon configuration
    ├── augments/                             # Augment JSON definitions
    ├── classes/                              # Class JSON definitions
    ├── passives/                             # Passive JSON definitions
    └── races/                                # Race JSON definitions
```

## JSON Format

All content uses JSON (matching the core Endless Leveling format). See the example files under `src/main/resources/` for the full schema.

### Race
```json
{
    "id": "my_race",
    "race_name": "My Race",
    "description": "...",
    "icon": "Ingredient_Life_Essence",
    "enabled": true,
    "attributes": {
        "life_force": 100.0,
        "strength": 1.0,
        "defense": 1.0
    },
    "passives": [
        { "type": "XP_BONUS", "value": 0.1 }
    ]
}
```

### Class
```json
{
    "id": "my_class",
    "class_name": "My Class",
    "description": "...",
    "role": "Fighter",
    "enabled": true,
    "icon": "Weapon_Sword_Copper",
    "weapons": [
        { "type": "SWORD", "damage": 1.1 }
    ],
    "passives": [
        { "type": "INNATE_ATTRIBUTE_GAIN", "attribute": "strength", "value": 0.5 }
    ]
}
```

### Augment
```json
{
    "id": "my_augment",
    "name": "My Augment",
    "tier": "COMMON",
    "category": "PASSIVE_STAT",
    "enabled": true,
    "passives": {
        "buffs": { "strength": { "value": 0.05 } }
    }
}
```

### Passive
```json
{
    "id": "my_passive",
    "type": "BERSERKER",
    "enabled": true,
    "value": 0.2,
    "threshold": 0.35
}
```

## Configuration

`config.json` controls which content systems are active and whether example content is registered:

```json
{
    "core_content_merge": {
        "races": true,
        "classes": true,
        "augments": true,
        "passives": true
    },
    "examples": {
        "enabled": false,
        "command": false,
        "events": false
    }
}
```

## Requirements

- **Endless Leveling** >= 7.2.0
- **Hytale Server** (2026.03.26+)
- **Java 25**
