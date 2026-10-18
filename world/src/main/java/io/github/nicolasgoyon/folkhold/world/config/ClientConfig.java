package io.github.nicolasgoyon.folkhold.world.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client configuration of the world mod: display options of each player. The territory HUD and the quest HUD show by
 * default.
 */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SHOW_TERRITORY_HUD;
    public static final ModConfigSpec.BooleanValue SHOW_QUEST_HUD;

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
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
