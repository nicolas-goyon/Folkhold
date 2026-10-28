package io.github.nicolasgoyon.folkhold.world.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client configuration of the world mod: display options of each player. The territory HUD, the quest HUD, the
 * discovered territories and villages on the maps of Xaero's mods, and the territories on the vanilla maps show by
 * default.
 */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SHOW_TERRITORY_HUD;
    public static final ModConfigSpec.BooleanValue SHOW_QUEST_HUD;
    public static final ModConfigSpec.BooleanValue SHOW_MAP_TERRITORIES;
    public static final ModConfigSpec.BooleanValue SHOW_MAP_VILLAGES;
    public static final ModConfigSpec.BooleanValue SHOW_VANILLA_MAP_TERRITORIES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SHOW_TERRITORY_HUD = builder
                .comment("Show the territory HUD at the top right of the screen: the faction that owns the",
                        "territory, your reputation tier and the village near you.")
                .translation(WorldConfig.KEY_PREFIX + "showTerritoryHud")
                .define("showTerritoryHud", true);
        SHOW_QUEST_HUD = builder
                .comment("Show the quest HUD at the top left of the screen: your active quests and the minutes",
                        "before their deadline.")
                .translation(WorldConfig.KEY_PREFIX + "showQuestHud")
                .define("showQuestHud", true);
        SHOW_MAP_TERRITORIES = builder
                .comment("Show the territories of the villages that you discovered on the maps of Xaero's Minimap",
                        "and Xaero's World Map, in the color of their faction.")
                .translation(WorldConfig.KEY_PREFIX + "showMapTerritories")
                .define("showMapTerritories", true);
        SHOW_MAP_VILLAGES = builder
                .comment("Show the villages that you discovered as waypoints of Xaero's Minimap. Xaero's World Map",
                        "also shows them.")
                .translation(WorldConfig.KEY_PREFIX + "showMapVillages")
                .define("showMapVillages", true);
        SHOW_VANILLA_MAP_TERRITORIES = builder
                .comment("Show the territories and the villages on the vanilla maps: the borders in the color of",
                        "their faction, and a marker at each village. The maps show all the territories in their area.")
                .translation(WorldConfig.KEY_PREFIX + "showVanillaMapTerritories")
                .define("showVanillaMapTerritories", true);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
