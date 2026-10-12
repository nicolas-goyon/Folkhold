package io.github.nicolasgoyon.folkhold.world.economy;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemCatalog;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Recipe;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * The jobs and the item values of the economy, from the data packs.
 *
 * <ul>
 *   <li>{@code data/<namespace>/folkhold/jobs/<id>.json}: one job. The job id is {@code <namespace>:<id>}.</li>
 *   <li>{@code data/folkhold_world/folkhold/items.json}: the price, the food points and the stock target of each
 *       item.</li>
 * </ul>
 *
 * <p>Job format:
 * <pre>
 * {
 *   "inputs": [{"item": "minecraft:wheat", "count": 6}, {"item": "minecraft:stone_pickaxe", "count": 1, "chance": 0.1}],
 *   "outputs": [{"item": "minecraft:bread", "count": 4, "chance": 1.0}],
 *   "min_level": 1,
 *   "weight": 10,
 *   "biomes": ["#minecraft:is_taiga", "minecraft:plains"],
 *   "profession": "minecraft:farmer"
 * }
 * </pre>
 * {@code inputs}, {@code chance} (1), {@code min_level} (1), {@code weight} (10), {@code biomes} (all biomes) and
 * {@code profession} are optional. {@code profession} is the villager profession that shows the job: an inhabitant
 * with this job has this profession (see {@link io.github.nicolasgoyon.folkhold.world.village.VillageFolk}). With no
 * profession, the inhabitant has the inhabitant profession. An input with a chance below 1 is a tool: the job needs it and uses it up sometimes. Items format: {@code {"minecraft:bread": {"price": 1.0, "food": 5, "target": 32}, ...}}.
 *
 * <p>A file that is not valid is ignored, with an error in the log.
 */
public final class EconomyData extends SimpleJsonResourceReloadListener {
    public static final String JOBS_DIRECTORY = "folkhold/jobs";
    public static final ResourceLocation ITEMS_FILE =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "folkhold/items.json");

    private static Map<String, Job> jobs = Map.of();
    private static ItemCatalog catalog = ItemCatalog.EMPTY;
    private static Map<String, ResourceLocation> professions = Map.of();

    public EconomyData() {
        super(new Gson(), JOBS_DIRECTORY);
    }

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new EconomyData());
    }

    /** The jobs, by id. */
    public static Map<String, Job> jobs() {
        return jobs;
    }

    /** The item values. */
    public static ItemCatalog catalog() {
        return catalog;
    }

    /**
     * The villager profession that shows a job ({@code profession} in the job file).
     *
     * @param jobId the job id
     * @return the profession id, or empty if the job file gives no profession
     */
    public static Optional<ResourceLocation> profession(String jobId) {
        return Optional.ofNullable(professions.get(jobId));
    }

    /** True if the data packs give at least one job. */
    public static boolean isActive() {
        return !jobs.isEmpty();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Job> loaded = new LinkedHashMap<>();
        Map<String, ResourceLocation> loadedProfessions = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().toString();
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                loaded.put(id, parseJob(id, json));
                if (json.has("profession")) {
                    loadedProfessions.put(id, ResourceLocation.parse(GsonHelper.getAsString(json, "profession")));
                }
            } catch (RuntimeException e) {
                FolkholdWorld.LOGGER.error("The job {} is not valid", id, e);
            }
        }
        jobs = Collections.unmodifiableMap(loaded);
        professions = Collections.unmodifiableMap(loadedProfessions);
        catalog = loadCatalog(manager);
        FolkholdWorld.LOGGER.info("Loaded {} jobs and {} item values", jobs.size(), catalog.all().size());
    }

    private static ItemCatalog loadCatalog(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(ITEMS_FILE);
        if (resource.isEmpty()) {
            return ItemCatalog.EMPTY;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return parseCatalog(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            FolkholdWorld.LOGGER.error("The item values {} are not valid", ITEMS_FILE, e);
            return ItemCatalog.EMPTY;
        }
    }

    /**
     * Reads a job.
     *
     * @param id   the job id
     * @param json the content of the file
     * @return the job
     * @throws RuntimeException if the content is not valid
     */
    public static Job parseJob(String id, JsonObject json) {
        List<Recipe.Input> inputs = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "inputs", new JsonArray())) {
            JsonObject input = element.getAsJsonObject();
            inputs.add(new Recipe.Input(GsonHelper.getAsString(input, "item"), GsonHelper.getAsInt(input, "count"),
                    GsonHelper.getAsDouble(input, "chance", 1.0)));
        }
        List<Recipe.Output> outputs = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "outputs")) {
            JsonObject output = element.getAsJsonObject();
            outputs.add(new Recipe.Output(GsonHelper.getAsString(output, "item"),
                    GsonHelper.getAsInt(output, "count"), GsonHelper.getAsDouble(output, "chance", 1.0)));
        }
        List<String> biomes = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "biomes", new JsonArray())) {
            biomes.add(element.getAsString());
        }
        return new Job(id, new Recipe(inputs, outputs), GsonHelper.getAsInt(json, "min_level", 1),
                GsonHelper.getAsInt(json, "weight", 10), biomes);
    }

    /**
     * Reads the item values.
     *
     * @param json the content of the file
     * @return the catalog
     * @throws RuntimeException if the content is not valid
     */
    public static ItemCatalog parseCatalog(JsonObject json) {
        Map<String, ItemInfo> items = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            JsonObject item = entry.getValue().getAsJsonObject();
            items.put(entry.getKey(), new ItemInfo(entry.getKey(), GsonHelper.getAsDouble(item, "price"),
                    GsonHelper.getAsInt(item, "food", 0), GsonHelper.getAsInt(item, "target", 32)));
        }
        return new ItemCatalog(items);
    }
}
