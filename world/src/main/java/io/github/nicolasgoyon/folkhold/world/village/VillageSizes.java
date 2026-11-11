package io.github.nicolasgoyon.folkhold.world.village;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.core.sim.SerializedName;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable;
import io.github.nicolasgoyon.folkhold.core.sim.level.Tier;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSize;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSizeTable;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
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
 * The size of the village structure of each tier (see {@link VillageSizeTable}), from the data file
 * {@code data/folkhold_world/folkhold/village_sizes.json}. A data pack can replace the file. If the file is missing or
 * not valid, the mod logs the error and uses {@link VillageSizeTable#DEFAULT}.
 *
 * <p>Format:
 * <pre>
 * {"sizes": [{"tier": "hamlet", "plots": 14, "spare_share": 0.15, "max_radius": 48, "rings": 0,
 *             "wall": "palisade", "min_territory_radius": 9}, ...]}
 * </pre>
 * {@code plots} is the plot target with the spare plots. In place of {@code plots}, {@code beds_per_plot} gives the
 * beds rule (see {@link VillageSize}). {@code spare_share} is from 0.1 to 0.2. {@code max_radius} is in blocks,
 * {@code min_territory_radius} in chunks. {@code wall} is {@code none} or {@code palisade}. The file must have a size
 * for each tier of the level table (see {@link LevelTables}).
 */
public final class VillageSizes extends SimplePreparableReloadListener<VillageSizeTable> {
    public static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "folkhold/village_sizes.json");

    private static VillageSizeTable current = VillageSizeTable.DEFAULT;

    /** The current table. */
    public static VillageSizeTable get() {
        return current;
    }

    /**
     * The village size of a level: the size of the tier of the level in the level table. A tier with no size in the
     * current table uses the size of {@link VillageSizeTable#DEFAULT}.
     *
     * @param level the level
     * @return the size
     */
    public static VillageSize forLevel(int level) {
        String tier = LevelTables.get().tier(level).id();
        return current.size(tier).or(() -> VillageSizeTable.DEFAULT.size(tier))
                .orElseThrow(() -> new IllegalStateException("No village size for the tier " + tier));
    }

    /**
     * Adds the listener. {@code FolkholdWorld} adds {@link LevelTables} before this listener, thus
     * {@link #apply} runs after the apply of the level table and checks the tiers of the new level table.
     */
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new VillageSizes());
    }

    /** Reads the file. The prepare step runs in parallel with the other listeners: it does not read the level table. */
    @Override
    protected VillageSizeTable prepare(ResourceManager manager, ProfilerFiller profiler) {
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) {
            FolkholdWorld.LOGGER.warn("No village size table {}: the mod uses the default table", FILE);
            return VillageSizeTable.DEFAULT;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            FolkholdWorld.LOGGER.error("The village size table {} is not valid: {}. The mod uses the default table",
                    FILE, e.getMessage());
            return VillageSizeTable.DEFAULT;
        }
    }

    @Override
    protected void apply(VillageSizeTable table, ResourceManager manager, ProfilerFiller profiler) {
        current = checked(table, LevelTables.get());
    }

    /**
     * Checks that a table has a size for each tier of a level table.
     *
     * @param table  the village size table
     * @param levels the level table
     * @return the table, or {@link VillageSizeTable#DEFAULT} (with an error in the log) when a tier has no size
     */
    static VillageSizeTable checked(VillageSizeTable table, LevelTable levels) {
        try {
            return table.requireTiers(levels.tiers().stream().map(Tier::id).toList());
        } catch (IllegalArgumentException e) {
            FolkholdWorld.LOGGER.error("The village size table {} is not valid: {}. The mod uses the default table",
                    FILE, e.getMessage());
            return VillageSizeTable.DEFAULT;
        }
    }

    /**
     * Reads a village size table.
     *
     * @param json the content of the file
     * @return the table
     * @throws RuntimeException if the content is not valid. The message gives the tier and the bad value.
     */
    public static VillageSizeTable parse(JsonObject json) {
        List<VillageSize> sizes = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "sizes")) {
            JsonObject size = GsonHelper.convertToJsonObject(element, "sizes");
            String tier = GsonHelper.getAsString(size, "tier");
            try {
                sizes.add(read(tier, size));
            } catch (JsonParseException e) {
                throw new IllegalArgumentException("The village size of the tier " + tier + " is not valid: "
                        + e.getMessage(), e);
            }
        }
        return new VillageSizeTable(sizes);
    }

    /** Reads the size of a tier. */
    private static VillageSize read(String tier, JsonObject size) {
        String wall = GsonHelper.getAsString(size, "wall");
        return new VillageSize(tier, GsonHelper.getAsInt(size, "plots", 0),
                    GsonHelper.getAsInt(size, "beds_per_plot", 0), GsonHelper.getAsDouble(size, "spare_share"),
                    GsonHelper.getAsInt(size, "max_radius"), GsonHelper.getAsInt(size, "rings"),
                    SerializedName.lookup(VillageSize.Wall.class, wall).orElseThrow(() -> new IllegalArgumentException(
                            "The village size of the tier " + tier + " has an unknown wall type " + wall)),
                    GsonHelper.getAsInt(size, "min_territory_radius"));
    }
}
