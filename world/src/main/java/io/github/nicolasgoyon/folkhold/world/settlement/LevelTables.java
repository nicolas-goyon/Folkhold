package io.github.nicolasgoyon.folkhold.world.settlement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable.StartChance;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTraits;
import io.github.nicolasgoyon.folkhold.core.sim.level.Tier;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * The level table of the settlements, from the data file {@code data/folkhold_world/folkhold/levels.json}. A data
 * pack can replace the file. If the file is missing or not valid, the mod uses {@link LevelTable#DEFAULT}.
 *
 * <p>Format:
 * <pre>
 * {
 *   "tiers": [{"id": "hamlet", "min_level": 1, "max_level": 4}, ...],
 *   "levels": [{"experience": 15, "claim_radius": 9, "max_population": 9, "job_slots": 2,
 *               "stock_capacity": 128, "trade_offers": 3, "cats": 1}, ...],
 *   "start_levels": [{"min_level": 1, "max_level": 1, "weight": 40}, ...]
 * }
 * </pre>
 * The first entry of {@code levels} is the level 1. {@code experience} is the experience to go to the next level.
 */
public final class LevelTables extends SimplePreparableReloadListener<LevelTable> {
    public static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "folkhold/levels.json");

    private static LevelTable current = LevelTable.DEFAULT;

    /** The current level table. */
    public static LevelTable get() {
        return current;
    }

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new LevelTables());
    }

    @Override
    protected LevelTable prepare(ResourceManager manager, ProfilerFiller profiler) {
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) {
            FolkholdWorld.LOGGER.warn("No level table {}: the mod uses the default table", FILE);
            return LevelTable.DEFAULT;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            FolkholdWorld.LOGGER.error("The level table {} is not valid: the mod uses the default table", FILE, e);
            return LevelTable.DEFAULT;
        }
    }

    @Override
    protected void apply(LevelTable table, ResourceManager manager, ProfilerFiller profiler) {
        current = table;
    }

    /**
     * Reads a level table.
     *
     * @param json the content of the file
     * @return the table
     * @throws RuntimeException if the content is not valid
     */
    public static LevelTable parse(JsonObject json) {
        List<Tier> tiers = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "tiers")) {
            JsonObject tier = element.getAsJsonObject();
            tiers.add(new Tier(GsonHelper.getAsString(tier, "id"), GsonHelper.getAsInt(tier, "min_level"),
                    GsonHelper.getAsInt(tier, "max_level")));
        }
        List<LevelTraits> levels = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "levels")) {
            JsonObject level = element.getAsJsonObject();
            levels.add(new LevelTraits(GsonHelper.getAsInt(level, "experience"),
                    GsonHelper.getAsInt(level, "claim_radius"), GsonHelper.getAsInt(level, "max_population"),
                    GsonHelper.getAsInt(level, "job_slots"), GsonHelper.getAsInt(level, "stock_capacity"),
                    GsonHelper.getAsInt(level, "trade_offers"), GsonHelper.getAsInt(level, "cats")));
        }
        JsonArray starts = GsonHelper.getAsJsonArray(json, "start_levels");
        List<StartChance> chances = new ArrayList<>();
        for (JsonElement element : starts) {
            JsonObject chance = element.getAsJsonObject();
            chances.add(new StartChance(GsonHelper.getAsInt(chance, "min_level"),
                    GsonHelper.getAsInt(chance, "max_level"), GsonHelper.getAsInt(chance, "weight")));
        }
        return new LevelTable(levels, tiers, chances);
    }
}
