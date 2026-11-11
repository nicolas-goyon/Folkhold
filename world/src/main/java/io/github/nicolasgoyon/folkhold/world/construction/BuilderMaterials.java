package io.github.nicolasgoyon.folkhold.world.construction;

import io.github.nicolasgoyon.folkhold.core.sim.construction.MaterialCosts;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemCatalog;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.ItemIds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The materials of the blocks of a construction site of the builders (see {@link BuilderSite}).
 *
 * <ul>
 *   <li>The materials of a block are the items of its state (see {@link #itemsOf}): usually its item, 2 slabs for a
 *       double slab, a flower pot and a plant for a pot with a plant. The cost of an item comes from the recipes of
 *       the server: the crafting, smelting and stonecutting recipes, and the axe that strips a log. The cost is in
 *       items of the economy (see {@link MaterialCosts}). Example: 1 oak stairs costs 3/8 of an oak log.</li>
 *   <li>A common block has a cost: the builders take it from the stock of the village.</li>
 *   <li>A rare block has no cost: no recipe makes it from the items of the economy, for example calcite. A valuable
 *       block has a cost at the price {@link WorldConfig#CONSTRUCTION_VALUABLE_PRICE} or more, for example a gold
 *       block. The builders take a rare or a valuable block as it is from the stock: the village asks the players for
 *       it with a quest.</li>
 *   <li>The blocks of the tag {@link #FREE}, for example the blocks of the village library, are free: the village
 *       makes them.</li>
 *   <li>With the setting {@link WorldConfig#CONSTRUCTION_FREE_MATERIALS}, or with no economy, all the blocks are
 *       free.</li>
 *   <li>The village keeps the blocks that the builders remove, as items of the economy (see
 *       {@link #removedItems}).</li>
 * </ul>
 *
 * <p>The costs are calculated once for the recipes and the economy of the server.
 */
public final class BuilderMaterials {
    /** The blocks that use no materials of the village: the village makes them. */
    public static final TagKey<Block> FREE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "construction/free_materials"));
    private static final String STRIPPED = "stripped_";

    @Nullable
    private static MaterialCosts costs;
    @Nullable
    private static RecipeManager costsRecipes;
    @Nullable
    private static ItemCatalog costsCatalog;
    private static final Map<Item, Material> MATERIALS = new HashMap<>();
    /** The costs of the drops of the removed blocks, by item: empty for an item with no cost. */
    private static final Map<Item, Optional<Map<String, Double>>> DROP_COSTS = new HashMap<>();
    /** The items of the block states (see {@link #itemsOf}). */
    private static final Map<BlockState, Map<Item, Integer>> STATE_ITEMS = new HashMap<>();
    /** The seeds of the loot tables for the items of a block state: the same drops for all the seeds are stable. */
    private static final int LOOT_SEEDS = 16;
    /** The prices of the settings for the materials in {@link #MATERIALS}: the valuable price and the rare price. */
    private static double materialsValuable = Double.NaN;
    private static double materialsRare = Double.NaN;

    private BuilderMaterials() {
    }

    /** The kind of the material of a block. */
    public enum Kind {
        /** The block uses no material of the village. */
        FREE,
        /** The builders make the block from items of the stock of the village. */
        COMMON,
        /** The builders take the block as it is from the stock: a rare or a valuable block. */
        RARE
    }

    /**
     * The material of a block.
     *
     * @param kind  the kind of the material
     * @param item  the id of the item of the block
     * @param cost  the cost of one block, in items of the economy: empty for a free block and a rare block
     * @param price the price of one block, in emeralds: 0 for a free block
     */
    public record Material(Kind kind, String item, Map<String, Double> cost, double price) {
        /** The material of a block that uses no material. */
        public static final Material NONE = new Material(Kind.FREE, "", Map.of(), 0.0);

        public Material {
            cost = Map.copyOf(cost);
        }
    }

    /** Forgets the costs of the server. */
    public static void onServerStopped(ServerStoppedEvent event) {
        costs = null;
        costsRecipes = null;
        costsCatalog = null;
        MATERIALS.clear();
        DROP_COSTS.clear();
        STATE_ITEMS.clear();
    }

    /** True if the builders use no materials: the setting, or no economy. */
    public static boolean areFree() {
        return WorldConfig.CONSTRUCTION_FREE_MATERIALS.get() || !EconomyData.isActive();
    }

    /**
     * The material of the item of a block.
     *
     * @param server the server: it gives the recipes
     * @param item   the item of the block, or air for a block with no item
     * @return the material
     */
    public static Material of(MinecraftServer server, Item item) {
        if (item == Items.AIR || areFree()
                || (item instanceof BlockItem block && block.getBlock().defaultBlockState().is(FREE))) {
            return Material.NONE;
        }
        MaterialCosts current = costs(server);
        double valuable = WorldConfig.CONSTRUCTION_VALUABLE_PRICE.get();
        double rare = WorldConfig.CONSTRUCTION_RARE_PRICE.get();
        if (valuable != materialsValuable || rare != materialsRare) {
            MATERIALS.clear();
            materialsValuable = valuable;
            materialsRare = rare;
        }
        return MATERIALS.computeIfAbsent(item, key -> {
            String id = ItemIds.id(key);
            Optional<Map<String, Double>> cost = current.cost(id);
            if (cost.isEmpty()) {
                return new Material(Kind.RARE, id, Map.of(), rare);
            }
            double price = current.price(cost.get());
            if (price >= valuable) {
                return new Material(Kind.RARE, id, Map.of(), price);
            }
            return new Material(Kind.COMMON, id, cost.get(), price);
        });
    }

    /**
     * The materials of the placement of a block state: one material for each item of the state (see
     * {@link #itemsOf}), with no free material. On the same block with another state, the placement costs only the
     * items that the new state has more than the block in place. Example: a bottom slab that becomes a double slab
     * costs 1 slab, and a change of the direction of stairs costs nothing.
     *
     * @param level the level
     * @param pos   the position of the block
     * @param state the state that the builders place
     * @return the materials, one for each item: empty for a block that uses no materials
     */
    public static List<Material> placement(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || areFree() || state.is(FREE)) {
            return List.of();
        }
        costs(level.getServer());
        Map<Item, Integer> items = new LinkedHashMap<>(itemsOf(level, pos, state));
        BlockState current = level.getBlockState(pos);
        if (current.is(state.getBlock())) {
            itemsOf(level, pos, current).forEach((item, count) -> items.computeIfPresent(item,
                    (key, needed) -> needed > count ? needed - count : null));
        }
        List<Material> materials = new ArrayList<>();
        items.forEach((item, count) -> {
            Material material = of(level.getServer(), item);
            if (material.kind() != Kind.FREE) {
                for (int i = 0; i < count; i++) {
                    materials.add(material);
                }
            }
        });
        return materials;
    }

    /**
     * The items of a block state, for its placement. The items come from the loot table of the state with no tool,
     * the same drops as for a removal (see {@link #removedItems}), when these drops are stable: the same items for
     * {@link #LOOT_SEEDS} seeds.
     *
     * <ul>
     *   <li>A block with an item: its stable drops when they are only this item, for example 2 slabs for a double
     *       slab, 4 candles for {@code candles=4}, 3 sea pickles for {@code pickles=3}. Else 1 item of the block, for
     *       example 1 stone (its drop is cobblestone) or 1 leaves block (its drops are random).</li>
     *   <li>A block with no item: its stable drops, for example a flower pot and a poppy for a pot with a poppy. With
     *       no stable drops, the block has no items.</li>
     *   <li>A cake with a candle also has a cake: its loot table drops only the candle.</li>
     * </ul>
     *
     * @param level the level: it gives the loot tables
     * @param pos   the position of the block, for the loot table
     * @param state the block state
     * @return the count of each item: empty for a block with no items
     */
    public static Map<Item, Integer> itemsOf(ServerLevel level, BlockPos pos, BlockState state) {
        Map<Item, Integer> known = STATE_ITEMS.get(state);
        if (known != null) {
            return known;
        }
        Item item = state.getBlock().asItem();
        Optional<Map<Item, Integer>> drops = stableDrops(level, pos, state);
        Map<Item, Integer> items = new LinkedHashMap<>();
        if (item == Items.AIR) {
            drops.ifPresent(items::putAll);
        } else if (drops.filter(found -> found.keySet().equals(Set.of(item))).isPresent()) {
            items.putAll(drops.get());
        } else {
            items.put(item, 1);
        }
        if (state.getBlock() instanceof CandleCakeBlock) {
            items.merge(Items.CAKE, 1, Integer::sum);
        }
        Map<Item, Integer> result = Collections.unmodifiableMap(items);
        STATE_ITEMS.put(state, result);
        return result;
    }

    /** The drops of a state with no tool and no block entity, if they are the same for all the seeds. */
    private static Optional<Map<Item, Integer>> stableDrops(ServerLevel level, BlockPos pos, BlockState state) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable());
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withParameter(LootContextParams.BLOCK_STATE, state).create(LootContextParamSets.BLOCK);
        Map<Item, Integer> first = null;
        for (long seed = 0; seed < LOOT_SEEDS; seed++) {
            Map<Item, Integer> drops = new LinkedHashMap<>();
            for (ItemStack drop : table.getRandomItems(params, seed)) {
                drops.merge(drop.getItem(), drop.getCount(), Integer::sum);
            }
            if (first == null) {
                first = drops;
            } else if (!first.equals(drops)) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(first);
    }

    /**
     * The items of the economy that the village keeps from the blocks that the builders remove, and the contents of
     * these blocks.
     *
     * <ul>
     *   <li>The drops of a block come from its loot table, with no tool: no silk touch, no shears and no fortune.
     *       Examples: stone drops cobblestone, a grass block drops dirt, leaves drop a sapling or sticks sometimes.
     *       A cake with a candle also gives a cake, as for its placement (see {@link #itemsOf}).</li>
     *   <li>Each drop gives its cost in items of the economy (see {@link MaterialCosts}): an item of the economy
     *       gives itself, and 1 oak stairs gives 1.5 oak planks. A block with stable drops of its item, or with no
     *       item, gives the items that its placement costs (see {@link #placement}). Thus the builders can remove
     *       and place again such a block with no gain: a double slab gives 2 slabs, a pot with a plant gives a pot
     *       and a plant.</li>
     *   <li>A drop with no cost is lost, for example calcite or a block of another mod with no recipe: the stock
     *       counts only the items of the economy.</li>
     *   <li>The contents of a container (a chest, a barrel, a furnace, a shulker box, a decorated pot, a jukebox, the
     *       book of a lectern, the items on a campfire) go out before the drops. An item with a cost and with no
     *       changed data gives its cost, as a drop. The other items fall in the level at the position of the block,
     *       for example a music disc, an enchanted book or a damaged tool: a player can take them. Such an item has
     *       no price in the economy, and the caravans must not sell it. A container with loot that is not made yet
     *       has no contents.</li>
     *   <li>Scaffolding and the blocks of the tag {@link #FREE} give nothing: they use no materials.</li>
     *   <li>When the village keeps nothing ({@code keep} false, or {@link #areFree}), the drops give nothing and all
     *       the contents fall in the level.</li>
     *   <li>The costs of the drops are kept for each item until the next change of the recipes or of the
     *       economy.</li>
     * </ul>
     *
     * @param level     the level
     * @param positions the positions of the blocks, before the removal: one block, or the two parts of a block
     * @param keep      true if the village keeps the drops and the contents
     * @return the quantity of each item of the economy, by item id
     */
    public static Map<String, Double> removedItems(ServerLevel level, List<BlockPos> positions, boolean keep) {
        @Nullable MaterialCosts current = keep && !areFree() ? costs(level.getServer()) : null;
        Map<String, Double> items = new TreeMap<>();
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(Blocks.SCAFFOLDING) || state.is(FREE)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            List<ItemStack> contents = contents(blockEntity);
            Clearable.tryClear(blockEntity);
            for (ItemStack content : contents) {
                if (current == null || !content.getComponentsPatch().isEmpty() || !addCost(items, current, content)) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, content);
                }
            }
            if (current == null) {
                continue;
            }
            for (ItemStack drop : Block.getDrops(state, level, pos, blockEntity)) {
                addCost(items, current, drop);
            }
            if (state.getBlock() instanceof CandleCakeBlock) {
                addCost(items, current, new ItemStack(Items.CAKE));
            }
        }
        return items;
    }

    /** Adds the cost of a stack to the items. Gives false for an item with no cost. */
    private static boolean addCost(Map<String, Double> items, MaterialCosts current, ItemStack stack) {
        Optional<Map<String, Double>> cost = DROP_COSTS.computeIfAbsent(stack.getItem(),
                key -> current.cost(ItemIds.id(key)));
        cost.ifPresent(found -> found.forEach((item, quantity) ->
                items.merge(item, quantity * stack.getCount(), Double::sum)));
        return cost.isPresent();
    }

    /**
     * The contents of a block entity: the items of a container, the book of a lectern, the items on a campfire. A
     * container with loot that is not made yet has no contents: the method does not make the loot.
     */
    private static List<ItemStack> contents(@Nullable BlockEntity blockEntity) {
        List<ItemStack> contents = new ArrayList<>();
        if (blockEntity instanceof RandomizableContainer loot && loot.getLootTable() != null) {
            return contents;
        }
        if (blockEntity instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                contents.add(container.getItem(slot).copy());
            }
        } else if (blockEntity instanceof LecternBlockEntity lectern) {
            contents.add(lectern.getBook().copy());
        } else if (blockEntity instanceof CampfireBlockEntity campfire) {
            campfire.getItems().forEach(stack -> contents.add(stack.copy()));
        }
        contents.removeIf(ItemStack::isEmpty);
        return contents;
    }

    /**
     * The costs of the server. The method makes them again after a reload of the recipes or of the economy.
     *
     * @param server the server
     * @return the costs
     */
    static MaterialCosts costs(MinecraftServer server) {
        RecipeManager recipes = server.getRecipeManager();
        ItemCatalog catalog = EconomyData.catalog();
        MaterialCosts current = costs;
        if (current == null || costsRecipes != recipes || costsCatalog != catalog) {
            current = new MaterialCosts(index(server, recipes), catalog::contains, id -> catalog.info(id).price());
            costs = current;
            costsRecipes = recipes;
            costsCatalog = catalog;
            MATERIALS.clear();
            DROP_COSTS.clear();
            STATE_ITEMS.clear();
        }
        return current;
    }

    /**
     * The recipes of the server, by the item that they make: the crafting, smelting and stonecutting recipes. A
     * stripped log comes from its log, as with an axe.
     */
    private static MaterialCosts.Recipes index(MinecraftServer server, RecipeManager manager) {
        Map<String, List<MaterialCosts.Recipe>> byOutput = new HashMap<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            RecipeType<?> type = recipe.getType();
            boolean used = type == RecipeType.CRAFTING || type == RecipeType.SMELTING
                    || type == RecipeType.STONECUTTING;
            if (!used || recipe.isSpecial()) {
                continue;
            }
            ItemStack result = recipe.getResultItem(server.registryAccess());
            Optional<List<MaterialCosts.Input>> inputs = inputs(recipe.getIngredients());
            if (result.isEmpty() || inputs.isEmpty()) {
                continue;
            }
            byOutput.computeIfAbsent(ItemIds.id(result.getItem()), key -> new ArrayList<>())
                    .add(new MaterialCosts.Recipe(inputs.get(), result.getCount()));
        }
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (!key.getPath().startsWith(STRIPPED)) {
                continue;
            }
            ResourceLocation source = key.withPath(key.getPath().substring(STRIPPED.length()));
            if (BuiltInRegistries.ITEM.containsKey(source)) {
                byOutput.computeIfAbsent(key.toString(), id -> new ArrayList<>()).add(new MaterialCosts.Recipe(
                        List.of(new MaterialCosts.Input(List.of(source.toString()), 1)), 1));
            }
        }
        return item -> byOutput.getOrDefault(item, List.of());
    }

    /**
     * The inputs of a recipe: the ingredients with the same items make one input. An ingredient with no item makes
     * the recipe impossible.
     */
    private static Optional<List<MaterialCosts.Input>> inputs(NonNullList<Ingredient> ingredients) {
        Map<List<String>, Integer> counts = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            List<String> options = Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty())
                    .map(stack -> ItemIds.id(stack.getItem())).distinct().sorted().toList();
            if (options.isEmpty()) {
                return Optional.empty();
            }
            counts.merge(options, 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            return Optional.empty();
        }
        List<MaterialCosts.Input> inputs = new ArrayList<>();
        counts.forEach((options, count) -> inputs.add(new MaterialCosts.Input(options, count)));
        return Optional.of(inputs);
    }
}
