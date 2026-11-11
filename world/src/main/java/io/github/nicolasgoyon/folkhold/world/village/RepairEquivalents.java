package io.github.nicolasgoyon.folkhold.world.village;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * The groups of equivalent blocks for the repairs (see {@link RepairRules}), from the data file
 * {@code data/folkhold_world/folkhold/repair_equivalents.json}. A data pack can replace the file. In a group, a block
 * can take the place of another block: the weather, the growth, the normal use or another mod changes one into the
 * other, and the repairs do not change it back.
 *
 * <p>Format:
 * <pre>
 * {"groups": [{"name": "cobblestone", "blocks": ["minecraft:cobblestone", "minecraft:mossy_cobblestone",
 *                                                "immersive_weathering:snowy_cobblestone"]},
 *             {"name": "soil", "blocks": ["#folkhold_world:construction/repair_soil", "trmt:eroded_grass_block"]}],
 *  "ignored_properties": ["weathering"]}
 * </pre>
 * An entry is a block id, or a block tag with the prefix {@code #}. The file skips a block id that is not in the block
 * registry (for example a block of a mod that is not loaded) and writes it to the debug log. A tag that no data pack
 * gives is empty. {@code ignored_properties} (optional) gives the names of the block state properties that the
 * comparison of the repairs ignores, in addition to the properties of {@link RepairRules}: the properties that another
 * mod adds to the vanilla blocks and changes. If the file is missing or not valid, the mod logs the error and uses no group.
 */
public final class RepairEquivalents extends SimplePreparableReloadListener<RepairEquivalents.Groups> {
    public static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "folkhold/repair_equivalents.json");

    private static Groups current = Groups.EMPTY;

    /** The current groups. */
    public static Groups get() {
        return current;
    }

    /** Adds the listener. */
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new RepairEquivalents());
    }

    /**
     * Reads the file of a resource manager and makes its groups the current groups.
     *
     * @param manager the resource manager of the server
     * @return the new current groups
     */
    public static Groups reload(ResourceManager manager) {
        current = load(manager);
        return current;
    }

    @Override
    protected Groups prepare(ResourceManager manager, ProfilerFiller profiler) {
        return load(manager);
    }

    @Override
    protected void apply(Groups groups, ResourceManager manager, ProfilerFiller profiler) {
        current = groups;
    }

    /**
     * Reads the file of a resource manager.
     *
     * @param manager the resource manager
     * @return the groups, or {@link Groups#EMPTY} (with an error in the log) when the file is missing or not valid
     */
    public static Groups load(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(FILE);
        if (resource.isEmpty()) {
            FolkholdWorld.LOGGER.error("No repair equivalents {}: the repairs use no group of equivalent blocks",
                    FILE);
            return Groups.EMPTY;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            FolkholdWorld.LOGGER.error("The repair equivalents {} are not valid: {}. The repairs use no group of "
                    + "equivalent blocks", FILE, e.getMessage());
            return Groups.EMPTY;
        }
    }

    /**
     * Reads the groups of equivalent blocks and the ignored properties.
     *
     * @param json the content of the file
     * @return the groups
     * @throws RuntimeException if the content is not valid, for example an entry that is not an id
     */
    public static Groups parse(JsonObject json) {
        List<Group> groups = new ArrayList<>();
        List<ResourceLocation> skipped = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "groups")) {
            JsonObject group = GsonHelper.convertToJsonObject(element, "groups");
            String name = GsonHelper.getAsString(group, "name", "group " + (groups.size() + 1));
            Set<Block> blocks = new LinkedHashSet<>();
            List<TagKey<Block>> tags = new ArrayList<>();
            for (JsonElement entry : GsonHelper.getAsJsonArray(group, "blocks")) {
                String text = GsonHelper.convertToString(entry, "blocks");
                if (text.startsWith("#")) {
                    tags.add(TagKey.create(Registries.BLOCK, ResourceLocation.parse(text.substring(1))));
                    continue;
                }
                ResourceLocation id = ResourceLocation.parse(text);
                if (BuiltInRegistries.BLOCK.containsKey(id)) {
                    blocks.add(BuiltInRegistries.BLOCK.get(id));
                } else {
                    FolkholdWorld.LOGGER.debug("Repair equivalents, group {}: no block {}, the group skips it", name,
                            id);
                    skipped.add(id);
                }
            }
            groups.add(new Group(name, Set.copyOf(blocks), List.copyOf(tags)));
        }
        Set<String> ignored = new LinkedHashSet<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "ignored_properties", new JsonArray())) {
            ignored.add(GsonHelper.convertToString(element, "ignored_properties"));
        }
        return new Groups(groups, skipped, ignored);
    }

    /**
     * A group of equivalent blocks.
     *
     * @param name   the name of the group, for the log
     * @param blocks the blocks of the group
     * @param tags   the block tags of the group
     */
    public record Group(String name, Set<Block> blocks, List<TagKey<Block>> tags) {
    }

    /** The groups of a block tag, by index. */
    private record TagGroups(TagKey<Block> tag, BitSet groups) {
    }

    /** The groups of the file, with an index from each block and each tag to its groups. */
    public static final class Groups {
        /** No group and no ignored property. */
        public static final Groups EMPTY = new Groups(List.of(), List.of(), Set.of());

        private final List<Group> groups;
        private final List<ResourceLocation> skipped;
        private final Set<String> ignoredProperties;
        private final Map<Block, BitSet> blockGroups = new HashMap<>();
        private final List<TagGroups> tagGroups = new ArrayList<>();

        /**
         * Makes the groups and their index.
         *
         * @param groups            the groups
         * @param skipped           the block ids of the file that are not in the block registry
         * @param ignoredProperties the names of the properties that the comparison of the repairs ignores
         */
        public Groups(List<Group> groups, List<ResourceLocation> skipped, Set<String> ignoredProperties) {
            this.groups = List.copyOf(groups);
            this.skipped = List.copyOf(skipped);
            this.ignoredProperties = Set.copyOf(ignoredProperties);
            Map<TagKey<Block>, BitSet> tags = new LinkedHashMap<>();
            for (int index = 0; index < this.groups.size(); index++) {
                Group group = this.groups.get(index);
                for (Block block : group.blocks()) {
                    blockGroups.computeIfAbsent(block, key -> new BitSet()).set(index);
                }
                for (TagKey<Block> tag : group.tags()) {
                    tags.computeIfAbsent(tag, key -> new BitSet()).set(index);
                }
            }
            tags.forEach((tag, bits) -> tagGroups.add(new TagGroups(tag, bits)));
        }

        /** The groups. */
        public List<Group> groups() {
            return groups;
        }

        /** The block ids of the file that are not in the block registry. */
        public List<ResourceLocation> skipped() {
            return skipped;
        }

        /** The names of the properties that the comparison of the repairs ignores. */
        public Set<String> ignoredProperties() {
            return ignoredProperties;
        }

        /**
         * True if two blocks are in one group.
         *
         * @param actual   the block of the level
         * @param expected the reference block
         * @return true if a group contains the two blocks
         */
        public boolean areEquivalent(BlockState actual, BlockState expected) {
            BitSet actualGroups = groupsOf(actual);
            if (actualGroups == null) {
                return false;
            }
            BitSet expectedGroups = groupsOf(expected);
            return expectedGroups != null && actualGroups.intersects(expectedGroups);
        }

        /** The indexes of the groups of a block, or null when no group contains it. */
        private BitSet groupsOf(BlockState state) {
            BitSet direct = blockGroups.get(state.getBlock());
            BitSet result = direct;
            for (TagGroups entry : tagGroups) {
                if (state.is(entry.tag())) {
                    if (result == direct) {
                        result = direct == null ? new BitSet() : (BitSet) direct.clone();
                    }
                    result.or(entry.groups());
                }
            }
            return result;
        }

        @Override
        public String toString() {
            return "Groups[groups=" + groups + ", skipped=" + skipped + ", ignoredProperties=" + ignoredProperties
                    + "]";
        }
    }
}
