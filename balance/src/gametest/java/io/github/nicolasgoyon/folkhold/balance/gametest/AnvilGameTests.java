package io.github.nicolasgoyon.folkhold.balance.gametest;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the anvil with the default config: vanilla merge rule with no level cap, exponential enchantment
 * cost (k = 0.5), repair cost with no prior work penalty. A mock player uses an anvil menu, and takes the result as a click
 * does. The tests use the template {@code folkhold_balance:bench}.
 */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnvilGameTests {
    private static final BlockPos ANVIL = new BlockPos(2, 1, 2);

    private AnvilGameTests() {
    }

    /**
     * A player at level 80 puts a book Efficiency VII on a pickaxe with Efficiency V. The result has Efficiency VII,
     * above the vanilla max level, and costs 1 + 0.5 × (2⁷ − 2⁵) = 49 levels. The take removes the levels one time and
     * empties the inputs.
     */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void efficiencySevenBookOnEfficiencyFiveCostsFortyNine(GameTestHelper helper) {
        ServerPlayer player = player(helper, 80);
        AnvilMenu menu = anvil(helper, player);
        ItemStack pickaxe = enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 5);
        menu.getSlot(0).set(pickaxe);
        menu.getSlot(1).set(book(helper, Enchantments.EFFICIENCY, 7));
        assertCost(helper, menu, 49);
        ItemStack result = take(helper, menu, player);
        helper.assertTrue(level(helper, result, Enchantments.EFFICIENCY) == 7, "The result is not Efficiency VII");
        helper.assertTrue(result.getOrDefault(DataComponents.REPAIR_COST, 0) == 0, "The result has a work penalty");
        helper.assertTrue(player.experienceLevel == 31, "The player has " + player.experienceLevel + " levels, not 31");
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(),
                "The anvil keeps an input item");
        remove(helper, player);
        helper.succeed();
    }

    /**
     * Two books with the same level give the level + 1 with no cap: V + V gives VI for 17 levels, VII + VII gives VIII
     * for 65 levels. 65 is above the vanilla limit of 39, and the player can take the result.
     */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void equalLevelsGiveTheLevelPlusOneWithNoCap(GameTestHelper helper) {
        ServerPlayer player = player(helper, 100);
        AnvilMenu menu = anvil(helper, player);
        menu.getSlot(0).set(book(helper, Enchantments.EFFICIENCY, 5));
        menu.getSlot(1).set(book(helper, Enchantments.EFFICIENCY, 5));
        assertCost(helper, menu, 17);
        ItemStack six = take(helper, menu, player);
        helper.assertTrue(storedLevel(helper, six, Enchantments.EFFICIENCY) == 6, "V + V does not give VI");

        menu.getSlot(0).set(book(helper, Enchantments.EFFICIENCY, 7));
        menu.getSlot(1).set(book(helper, Enchantments.EFFICIENCY, 7));
        assertCost(helper, menu, 65);
        ItemStack eight = take(helper, menu, player);
        helper.assertTrue(storedLevel(helper, eight, Enchantments.EFFICIENCY) == 8, "VII + VII does not give VIII");
        helper.assertTrue(player.experienceLevel == 100 - 17 - 65, "The levels are not taken exactly once");
        remove(helper, player);
        helper.succeed();
    }

    /**
     * Two repairs of the same pickaxe (Efficiency II, Unbreaking III) with the same damage cost the same: 3 units of
     * diamond cost 9 levels each time. The anvil uses 3 diamonds each time. A full repair costs 12 levels.
     */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void repairCostStaysTheSame(GameTestHelper helper) {
        ServerPlayer player = player(helper, 50);
        AnvilMenu menu = anvil(helper, player);
        ItemStack pickaxe = enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 2);
        pickaxe.enchant(enchantment(helper, Enchantments.UNBREAKING), 3);
        for (int repair = 0; repair < 2; repair++) {
            pickaxe.setDamageValue(1000);
            menu.getSlot(0).set(pickaxe);
            menu.getSlot(1).set(new ItemStack(Items.DIAMOND, 10));
            assertCost(helper, menu, 9);
            pickaxe = take(helper, menu, player);
            // 3 units: 390 + 390 + 220 points of durability.
            helper.assertTrue(pickaxe.getDamageValue() == 0, "The damage after the repair is " + pickaxe.getDamageValue());
            helper.assertTrue(menu.getSlot(1).getItem().getCount() == 7,
                    "The anvil leaves " + menu.getSlot(1).getItem().getCount() + " diamonds, not 7");
            menu.getSlot(1).set(ItemStack.EMPTY);
        }
        helper.assertTrue(player.experienceLevel == 50 - 18, "The two repairs do not cost 9 levels each");

        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
        menu.getSlot(0).set(pickaxe);
        menu.getSlot(1).set(new ItemStack(Items.DIAMOND, 4));
        assertCost(helper, menu, 12);
        ItemStack repaired = take(helper, menu, player);
        helper.assertTrue(repaired.getDamageValue() == 0, "The full repair leaves damage");
        helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "The full repair does not use the 4 diamonds");
        remove(helper, player);
        helper.succeed();
    }

    /**
     * Two damaged pickaxes: the left one has Efficiency II, the right one has Unbreaking III. The durability is the
     * vanilla sum (+12 %). The cost is the repair of 2 quarters of the left pickaxe (3 levels) plus Unbreaking III
     * (9 levels).
     */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void twoDamagedItemsCombine(GameTestHelper helper) {
        ServerPlayer player = player(helper, 30);
        AnvilMenu menu = anvil(helper, player);
        ItemStack left = enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 2);
        left.setDamageValue(1000);
        ItemStack right = enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.UNBREAKING, 3);
        right.setDamageValue(1000);
        menu.getSlot(0).set(left);
        menu.getSlot(1).set(right);
        assertCost(helper, menu, 12);
        ItemStack result = take(helper, menu, player);
        helper.assertTrue(result.getDamageValue() == 1561 - (561 + 561 + 1561 * 12 / 100),
                "The damage of the result is " + result.getDamageValue());
        helper.assertTrue(level(helper, result, Enchantments.EFFICIENCY) == 2
                && level(helper, result, Enchantments.UNBREAKING) == 3, "The result does not have both enchantments");
        remove(helper, player);
        helper.succeed();
    }

    /** A rename of an item with a high vanilla work penalty costs 1 level. */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void renameIgnoresTheWorkPenalty(GameTestHelper helper) {
        ServerPlayer player = player(helper, 5);
        AnvilMenu menu = anvil(helper, player);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(DataComponents.REPAIR_COST, 63);
        menu.getSlot(0).set(sword);
        menu.setItemName("Old friend");
        assertCost(helper, menu, 1);
        ItemStack result = take(helper, menu, player);
        helper.assertTrue(result.getHoverName().getString().equals("Old friend"), "The sword has no new name");
        helper.assertTrue(result.getOrDefault(DataComponents.REPAIR_COST, -1) == 0, "The penalty stays on the sword");
        helper.assertTrue(player.experienceLevel == 4, "The rename does not cost 1 level");
        remove(helper, player);
        helper.succeed();
    }

    /** A book with only incompatible enchantments gives no result: Fortune on a pickaxe with Silk Touch. */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void incompatibleBookGivesNoResult(GameTestHelper helper) {
        ServerPlayer player = player(helper, 50);
        AnvilMenu menu = anvil(helper, player);
        menu.getSlot(0).set(enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH, 1));
        menu.getSlot(1).set(book(helper, Enchantments.FORTUNE, 3));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "The anvil gives a result");
        remove(helper, player);
        helper.succeed();
    }

    /**
     * A cost above 32,767 levels (the largest value that the menu sends) gives no result. Efficiency 255 does not
     * overflow the calculation.
     */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void costAboveTheLimitGivesNoResult(GameTestHelper helper) {
        ServerPlayer player = player(helper, 50);
        AnvilMenu menu = anvil(helper, player);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
        // 1 + 0.5 × 2²⁵⁵ levels.
        menu.getSlot(1).set(book(helper, Enchantments.EFFICIENCY, 255));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "The anvil gives a result for Efficiency 255");
        // 1 + 0.5 × 2¹⁶ = 32,769 levels.
        menu.getSlot(1).set(book(helper, Enchantments.EFFICIENCY, 16));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "The anvil gives a result for 32,769 levels");
        // 1 + 0.5 × 2¹⁵ = 16,385 levels: below the limit.
        menu.getSlot(1).set(book(helper, Enchantments.EFFICIENCY, 15));
        assertCost(helper, menu, 16385);
        remove(helper, player);
        helper.succeed();
    }

    /** A player with too few levels cannot take the result: no levels and no items change. */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void tooFewLevelsTakeNothing(GameTestHelper helper) {
        ServerPlayer player = player(helper, 5);
        AnvilMenu menu = anvil(helper, player);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
        menu.getSlot(1).set(book(helper, Enchantments.UNBREAKING, 3));
        assertCost(helper, menu, 9);
        menu.clicked(AnvilMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty(), "The player takes the result with too few levels");
        helper.assertTrue(player.experienceLevel == 5, "The player loses levels");
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.DIAMOND_PICKAXE)
                && menu.getSlot(1).getItem().is(Items.ENCHANTED_BOOK), "The anvil loses an input item");
        remove(helper, player);
        helper.succeed();
    }

    /** In creative mode, a book on a stack of items gives no result: the result would enchant the full stack. */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void stackIsNotEnchanted(GameTestHelper helper) {
        ServerPlayer player = player(helper, 0);
        player.setGameMode(GameType.CREATIVE);
        AnvilMenu menu = anvil(helper, player);
        menu.getSlot(0).set(new ItemStack(Items.STICK, 16));
        menu.getSlot(1).set(book(helper, Enchantments.SHARPNESS, 1));
        helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "The anvil enchants a stack");
        remove(helper, player);
        helper.succeed();
    }

    /** A level with no translation shows as a number. */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void highLevelShowsAsANumber(GameTestHelper helper) {
        Component name = Enchantment.getFullname(enchantment(helper, Enchantments.EFFICIENCY), 20);
        helper.assertTrue(name.getString().endsWith(" 20"), "The name is " + name.getString());
        Component five = Enchantment.getFullname(enchantment(helper, Enchantments.EFFICIENCY), 5);
        helper.assertTrue(five.getString().endsWith(" V"), "The name is " + five.getString());
        helper.succeed();
    }

    /** A book of Sharpness on a pickaxe gives no result: a pickaxe does not support Sharpness. */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void unsupportedBookGivesNoResult(GameTestHelper helper) {
        ServerPlayer player = player(helper, 50);
        AnvilMenu menu = anvil(helper, player);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
        menu.getSlot(1).set(book(helper, Enchantments.SHARPNESS, 5));
        helper.assertTrue(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(), "The anvil gives a result");
        remove(helper, player);
        helper.succeed();
    }

    /**
     * A book with Unbreaking III and Sharpness V on a pickaxe: the pickaxe gets Unbreaking III only, for 9 levels.
     * An enchantment that the item does not support adds no cost.
     */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void bookAppliesOnlyTheSupportedEnchantments(GameTestHelper helper) {
        ServerPlayer player = player(helper, 50);
        AnvilMenu menu = anvil(helper, player);
        ItemStack book = book(helper, Enchantments.UNBREAKING, 3);
        Holder<Enchantment> sharpness = enchantment(helper, Enchantments.SHARPNESS);
        EnchantmentHelper.updateEnchantments(book, enchantments -> enchantments.set(sharpness, 5));
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
        menu.getSlot(1).set(book);
        assertCost(helper, menu, 9);
        ItemStack result = take(helper, menu, player);
        helper.assertTrue(level(helper, result, Enchantments.UNBREAKING) == 3, "The pickaxe has no Unbreaking III");
        helper.assertTrue(level(helper, result, Enchantments.SHARPNESS) == 0, "The pickaxe has Sharpness");
        remove(helper, player);
        helper.succeed();
    }

    /**
     * An iron pickaxe (250 durability) at a damage of 249 with 10 iron ingots: 4 units of 62 points leave 1 point of
     * damage, so the vanilla loop uses a fifth ingot. The cost counts 4 units at most: a full repair, 3 levels.
     */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void ironPickaxeUsesFiveIngots(GameTestHelper helper) {
        ServerPlayer player = player(helper, 10);
        AnvilMenu menu = anvil(helper, player);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.setDamageValue(249);
        menu.getSlot(0).set(pickaxe);
        menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 10));
        assertCost(helper, menu, 3);
        ItemStack result = take(helper, menu, player);
        helper.assertTrue(result.getDamageValue() == 0, "The damage after the repair is " + result.getDamageValue());
        helper.assertTrue(menu.getSlot(1).getItem().getCount() == 5,
                "The anvil leaves " + menu.getSlot(1).getItem().getCount() + " ingots, not 5");
        helper.assertTrue(player.experienceLevel == 7, "The repair does not cost 3 levels");
        remove(helper, player);
        helper.succeed();
    }

    /** A shift-click on the result moves it to the inventory, takes the levels one time and empties the inputs. */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void shiftClickTakesTheResult(GameTestHelper helper) {
        ServerPlayer player = player(helper, 30);
        AnvilMenu menu = anvil(helper, player);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
        menu.getSlot(1).set(book(helper, Enchantments.UNBREAKING, 3));
        assertCost(helper, menu, 9);
        menu.clicked(AnvilMenu.RESULT_SLOT, 0, ClickType.QUICK_MOVE, player);
        int pickaxes = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.DIAMOND_PICKAXE)) {
                pickaxes++;
                helper.assertTrue(level(helper, stack, Enchantments.UNBREAKING) == 3, "The pickaxe has no Unbreaking");
            }
        }
        helper.assertTrue(pickaxes == 1, "The inventory has " + pickaxes + " pickaxes, not 1");
        helper.assertTrue(player.experienceLevel == 21, "The player has " + player.experienceLevel + " levels");
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
                && menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(), "The anvil keeps an item");
        remove(helper, player);
        helper.succeed();
    }

    /**
     * The anvil recipe of another mod (a test listener: dirt + stick gives a diamond for 3 levels) keeps its output:
     * the listener of the balance mod runs last and does nothing when an output is set.
     */
    @GameTest(template = "bench", batch = "balance_anvil")
    public static void recipeOfAnotherModStays(GameTestHelper helper) {
        registerTestRecipe();
        ServerPlayer player = player(helper, 5);
        AnvilMenu menu = anvil(helper, player);
        menu.getSlot(0).set(new ItemStack(Items.DIRT));
        menu.getSlot(1).set(new ItemStack(Items.STICK, 4));
        helper.assertTrue(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().is(Items.DIAMOND),
                "The output of the other recipe is lost");
        assertCost(helper, menu, 3);
        take(helper, menu, player);
        helper.assertTrue(menu.getSlot(1).getItem().getCount() == 3, "The recipe does not use 1 stick");
        helper.assertTrue(player.experienceLevel == 2, "The recipe does not cost 3 levels");
        remove(helper, player);
        helper.succeed();
    }

    /**
     * With {@code enabled = false}, the anvil is vanilla: a book costs half its anvil cost for each level, the result
     * gets a work penalty, the level stays at the max level, and a level above X has no number.
     */
    @GameTest(template = "bench", batch = "balance_anvil_vanilla")
    public static void disabledRulesGiveTheVanillaAnvil(GameTestHelper helper) {
        BalanceTestSupport.setConfigForTest(helper, BalanceConfig.ANVIL_ENABLED, false);
        ServerPlayer player = player(helper, 30);
        AnvilMenu menu = anvil(helper, player);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
        menu.getSlot(1).set(book(helper, Enchantments.UNBREAKING, 3));
        // Vanilla: max(1, 2 / 2) × 3 = 3 levels.
        assertCost(helper, menu, 3);
        ItemStack result = take(helper, menu, player);
        helper.assertTrue(result.getOrDefault(DataComponents.REPAIR_COST, 0) == 1, "The result has no penalty");

        menu.getSlot(0).set(enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.EFFICIENCY, 5));
        menu.getSlot(1).set(book(helper, Enchantments.EFFICIENCY, 10));
        // Vanilla: the level stays at V, max(1, 1 / 2) × 5 = 5 levels.
        assertCost(helper, menu, 5);
        helper.assertTrue(level(helper, menu.getSlot(AnvilMenu.RESULT_SLOT).getItem(), Enchantments.EFFICIENCY) == 5,
                "The vanilla anvil goes above the max level");
        String name = Enchantment.getFullname(enchantment(helper, Enchantments.EFFICIENCY), 20).getString();
        helper.assertTrue(name.endsWith("enchantment.level.20"), "The name is " + name);
        remove(helper, player);
        helper.succeed();
    }

    private static boolean testRecipeRegistered;

    /** Registers the test recipe one time, at normal priority, as a mod does. */
    private static void registerTestRecipe() {
        if (!testRecipeRegistered) {
            NeoForge.EVENT_BUS.addListener(AnvilGameTests::testRecipe);
            testRecipeRegistered = true;
        }
    }

    private static void testRecipe(AnvilUpdateEvent event) {
        if (event.getLeft().is(Items.DIRT) && event.getRight().is(Items.STICK)) {
            event.setOutput(new ItemStack(Items.DIAMOND));
            event.setCost(3);
            event.setMaterialCost(1);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, int levels) {
        ServerPlayer player = TrainGameTests.survivalPlayer(helper, new Vec3(2.5, 1, 4.5));
        player.setExperienceLevels(levels);
        return player;
    }

    private static AnvilMenu anvil(GameTestHelper helper, ServerPlayer player) {
        helper.setBlock(ANVIL, Blocks.ANVIL);
        return new AnvilMenu(1, player.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(ANVIL)));
    }

    private static void assertCost(GameTestHelper helper, AnvilMenu menu, int cost) {
        helper.assertFalse(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(), "The anvil gives no result");
        helper.assertTrue(menu.getCost() == cost, "The cost is " + menu.getCost() + ", not " + cost);
    }

    /** Takes the result with a click, as a player does, and gives the item that the player holds. */
    private static ItemStack take(GameTestHelper helper, AnvilMenu menu, ServerPlayer player) {
        menu.clicked(AnvilMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
        ItemStack carried = menu.getCarried();
        helper.assertFalse(carried.isEmpty(), "The player cannot take the result");
        menu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(), "The result slot keeps an item");
        return carried;
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    private static ItemStack enchanted(GameTestHelper helper, Item item,
            ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(enchantment(helper, key), level);
        return stack;
    }

    private static ItemStack book(GameTestHelper helper, ResourceKey<Enchantment> key, int level) {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment(helper, key), level));
    }

    private static int level(GameTestHelper helper, ItemStack stack, ResourceKey<Enchantment> key) {
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .getLevel(enchantment(helper, key));
    }

    private static int storedLevel(GameTestHelper helper, ItemStack stack, ResourceKey<Enchantment> key) {
        return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                .getLevel(enchantment(helper, key));
    }
}
