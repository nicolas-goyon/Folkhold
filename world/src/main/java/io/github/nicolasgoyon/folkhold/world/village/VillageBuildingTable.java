package io.github.nicolasgoyon.folkhold.world.village;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings.Requirement;
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
 * The buildings that a village needs for its level (see {@link VillageBuildings}), from the data file
 * {@code data/folkhold_world/folkhold/village_buildings.json}. A data pack can replace the file. If the file is missing
 * or not valid, the villages need no building.
 *
 * <p>Format:
 * <pre>
 * {"buildings": [{"kind": "storehouse", "min_level": 2}, {"kind": "forge", "min_level": 5}, ...]}
 * </pre>
 * {@code kind} is the kind of the building sign of the building. The template of the building is the piece
 * {@code house_<kind>} of the variant of the village.
 */
public final class VillageBuildingTable extends SimplePreparableReloadListener<List<Requirement>> {
    public static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "folkhold/village_buildings.json");

    private static List<Requirement> current = List.of();

    /** The current requirements, in their order. */
    public static List<Requirement> get() {
        return current;
    }

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new VillageBuildingTable());
    }

    @Override
    protected List<Requirement> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) {
            FolkholdWorld.LOGGER.warn("No village building table {}: the villages need no building", FILE);
            return List.of();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            FolkholdWorld.LOGGER.error("The village building table {} is not valid: the villages need no building",
                    FILE, e);
            return List.of();
        }
    }

    @Override
    protected void apply(List<Requirement> requirements, ResourceManager manager, ProfilerFiller profiler) {
        current = requirements;
    }

    /**
     * Reads the requirements.
     *
     * @param json the content of the file
     * @return the requirements, in their order
     * @throws RuntimeException if the content is not valid
     */
    public static List<Requirement> parse(JsonObject json) {
        List<Requirement> requirements = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "buildings")) {
            JsonObject building = element.getAsJsonObject();
            requirements.add(new Requirement(GsonHelper.getAsString(building, "kind"),
                    GsonHelper.getAsInt(building, "min_level")));
        }
        return List.copyOf(requirements);
    }
}
