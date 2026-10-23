package io.github.nicolasgoyon.folkhold.world.construction;

import io.github.nicolasgoyon.folkhold.core.sim.construction.MaterialCosts;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemCatalog;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.ItemIds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The materials of the blocks of a construction site of the builders (see {@link BuilderSite}).
 *
 * <ul>
 *   <li>The material of a block is its item. The cost of the item comes from the recipes of the server: the crafting,
 *       smelting and stonecutting recipes, and the axe that strips a log. The cost is in items of the economy (see
 *       {@link MaterialCosts}). Example: 1 oak stairs costs 3/8 of an oak log.</li>
 *   <li>A common block has a cost: the builders take it from the stock of the village.</li>
 *   <li>A rare block has no cost: no recipe makes it from the items of the economy, for example calcite. A valuable
 *       block has a cost at the price {@link WorldConfig#CONSTRUCTION_VALUABLE_PRICE} or more, for example a gold
 *       block. The builders take a rare or a valuable block as it is from the stock: the village asks the players for
 *       it with a quest.</li>
 *   <li>With the setting {@link WorldConfig#CONSTRUCTION_FREE_MATERIALS}, or with no economy, all the blocks are
 *       free.</li>
 * </ul>
 *
 * <p>The costs are calculated once for the recipes and the economy of the server.
 */
public final class BuilderMaterials {
    private static final String STRIPPED = "stripped_";

    @Nullable
    private static MaterialCosts costs;
    @Nullable
    private static RecipeManager costsRecipes;
    @Nullable
    private static ItemCatalog costsCatalog;
    private static final Map<Item, Material> MATERIALS = new HashMap<>();
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
        if (item == Items.AIR || areFree()) {
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
