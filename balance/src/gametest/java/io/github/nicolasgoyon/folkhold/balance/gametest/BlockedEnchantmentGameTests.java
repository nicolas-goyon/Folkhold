package io.github.nicolasgoyon.folkhold.balance.gametest;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.enchantment.FilteredListing;
import io.github.nicolasgoyon.folkhold.balance.enchantment.VillagerOffers;
import io.github.nicolasgoyon.folkhold.balance.mixin.AbstractVillagerAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerTrades.ItemListing;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the blocked enchantments (default config: Mending only). The tests use the template
 * {@code folkhold_balance:bench}.
 */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockedEnchantmentGameTests {
    private static final int ROLLS = 1000;
    /** The number of offers from each librarian listing. */
    private static final int LISTING_ROLLS = 200;

    private BlockedEnchantmentGameTests() {
    }

    /** The filter wraps each listing of each profession and of the wandering trader. */
    @GameTest(template = "bench", batch = "balance_enchantments")
    public static void allTradeListingsAreFiltered(GameTestHelper helper) {
        VillagerTrades.TRADES.forEach((profession, levels) -> levels.values().forEach(listings -> {
            for (ItemListing listing : listings) {
                helper.assertTrue(listing instanceof FilteredListing,
                        "A listing of " + profession + " is not filtered: " + listing);
            }
        }));
        VillagerTrades.WANDERING_TRADER_TRADES.values().forEach(listings -> {
            for (ItemListing listing : listings) {
                helper.assertTrue(listing instanceof FilteredListing, "A listing of the wandering trader is not filtered");
            }
        });
        helper.succeed();
    }

    /**
     * 1,000 vanilla book offers with fixed seeds contain Mending. With the filter, the same 1,000 offers are all
     * enchanted books, with no Mending. The librarian listings of all levels never give Mending.
     */
    @GameTest(template = "bench", batch = "balance_enchantments")
    public static void bookOffersHaveNoMending(GameTestHelper helper) {
        Villager librarian = helper.spawn(EntityType.VILLAGER, new Vec3(2.5, 1, 2.5));
        librarian.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 1));
        ItemListing vanilla = new VillagerTrades.EnchantBookForEmeralds(1, EnchantmentTags.TRADEABLE);
        ItemListing filtered = new FilteredListing(vanilla);
        int vanillaMending = 0;
        for (int seed = 1; seed <= ROLLS; seed++) {
            MerchantOffer before = vanilla.getOffer(librarian, RandomSource.create(seed));
            if (hasMending(before.getResult())) {
                vanillaMending++;
            }
            MerchantOffer after = filtered.getOffer(librarian, RandomSource.create(seed));
            helper.assertTrue(after != null, "The filtered listing gives no offer for the seed " + seed);
            helper.assertTrue(after.getResult().is(Items.ENCHANTED_BOOK), "The offer is not an enchanted book");
            helper.assertFalse(hasMending(after.getResult()), "A filtered book offer has Mending, seed " + seed);
        }
        helper.assertTrue(vanillaMending > 0, "The vanilla offers have no Mending: the test proves nothing");
        RandomSource random = RandomSource.create(7L);
        for (ItemListing[] listings : VillagerTrades.TRADES.get(VillagerProfession.LIBRARIAN).values()) {
            for (ItemListing listing : listings) {
                for (int i = 0; i < LISTING_ROLLS; i++) {
                    MerchantOffer offer = listing.getOffer(librarian, random);
                    helper.assertTrue(offer == null || !hasMending(offer.getResult()), "A librarian offer has Mending");
                }
            }
        }
        librarian.discard();
        helper.succeed();
    }

    /**
     * A villager joins the level with a Mending book offer, a Mending pickaxe offer and an offer with no enchantment.
     * The book gets a different enchantment and a vanilla price. The pickaxe loses Mending and gets a different
     * enchantment that a pickaxe supports, at the same price. The other values of the offers stay the same.
     */
    @GameTest(template = "bench", batch = "balance_enchantments")
    public static void existingMendingOffersAreReplaced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Enchantment> mending = enchantment(level, Enchantments.MENDING);
        Holder<Enchantment> efficiency = enchantment(level, Enchantments.EFFICIENCY);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.enchant(mending, 1);
        pickaxe.enchant(efficiency, 3);
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 40), Optional.of(new ItemCost(Items.BOOK)),
                EnchantedBookItem.createForEnchantment(new EnchantmentInstance(mending, 1)), 3, 12, 5, 0.2F, 2));
        offers.add(new MerchantOffer(new ItemCost(Items.PAPER, 24), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 10), Optional.empty(), pickaxe, 1, 3, 10, 0.2F, 0));

        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 2));
        villager.setOffers(offers);
        Vec3 position = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        villager.moveTo(position.x, position.y, position.z);
        level.addFreshEntity(villager);

        MerchantOffers after = villager.getOffers();
        helper.assertTrue(after.size() == 3, "The villager has " + after.size() + " offers, not 3");

        MerchantOffer book = after.get(0);
        ItemEnchantments stored = book.getResult().getOrDefault(DataComponents.STORED_ENCHANTMENTS,
                ItemEnchantments.EMPTY);
        helper.assertTrue(stored.size() == 1, "The book has " + stored.size() + " enchantments");
        Holder<Enchantment> replacement = stored.keySet().iterator().next();
        helper.assertFalse(replacement.is(Enchantments.MENDING), "The book still has Mending");
        helper.assertTrue(replacement.is(EnchantmentTags.TRADEABLE), "The new enchantment is not tradeable");
        helper.assertTrue(stored.getLevel(replacement) == 1, "The level of the new enchantment is not 1");
        int price = book.getItemCostA().count();
        int factor = replacement.is(EnchantmentTags.DOUBLE_TRADE_PRICE) ? 2 : 1;
        helper.assertTrue(book.getItemCostA().itemStack().is(Items.EMERALD), "The book is not sold for emeralds");
        helper.assertTrue(price >= 5 * factor && price <= Math.min(64, 19 * factor), "The book price is " + price);
        helper.assertTrue(book.getCostB().is(Items.BOOK), "The book offer lost its second cost");
        helper.assertTrue(book.getUses() == 3 && book.getMaxUses() == 12 && book.getXp() == 5
                && book.getDemand() == 2, "The book offer lost its uses, experience or demand");

        MerchantOffer paper = after.get(1);
        helper.assertTrue(paper.getItemCostA().count() == 24 && paper.getResult().is(Items.EMERALD),
                "The offer with no enchantment changed");

        MerchantOffer tool = after.get(2);
        ItemEnchantments toolEnchantments = tool.getResult().getOrDefault(DataComponents.ENCHANTMENTS,
                ItemEnchantments.EMPTY);
        helper.assertFalse(toolEnchantments.getLevel(mending) > 0, "The pickaxe still has Mending");
        helper.assertTrue(toolEnchantments.getLevel(efficiency) == 3, "The pickaxe lost Efficiency III");
        helper.assertTrue(toolEnchantments.size() == 2, "The pickaxe did not get a new enchantment");
        for (Holder<Enchantment> enchantment : toolEnchantments.keySet()) {
            helper.assertTrue(tool.getResult().supportsEnchantment(enchantment),
                    "A pickaxe does not support " + enchantment);
        }
        helper.assertTrue(tool.getItemCostA().count() == 10 && tool.getUses() == 1,
                "The pickaxe offer changed its price or its uses");
        helper.assertTrue(VillagerOffers.clean(villager) == 0, "A second clean changes the offers again");
        villager.discard();
        helper.succeed();
    }

    /** A villager with no offers yet joins the level: the filter does not make its offers. */
    @GameTest(template = "bench", batch = "balance_enchantments")
    public static void villagerWithNoOffersStaysWithNoOffers(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new Vec3(2.5, 1, 2.5));
        helper.assertTrue(((AbstractVillagerAccessor) villager).folkhold_balance$offers() == null,
                "The villager has offers after it joins the level");
        villager.discard();
        helper.succeed();
    }

    /**
     * 1,000 rolls of the fishing table (with a high luck, so that most rolls are treasure) with fixed seeds. The raw
     * loot contains Mending. The loot after the modifiers has the same items, with no Mending.
     */
    @GameTest(template = "bench", batch = "balance_enchantments")
    // getRandomItemsRaw is deprecated because it does not apply the loot modifiers: the test needs the raw loot.
    @SuppressWarnings("deprecation")
    public static void fishingGivesNoMending(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(2.5, 1, 2.5)))
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                .withParameter(LootContextParams.THIS_ENTITY, new FishingHook(EntityType.FISHING_BOBBER, level))
                .withLuck(100.0F)
                .create(LootContextParamSets.FISHING);
        int rawMending = 0;
        for (int seed = 1; seed <= ROLLS; seed++) {
            List<ItemStack> raw = new ArrayList<>();
            Consumer<ItemStack> output = raw::add;
            table.getRandomItemsRaw(new LootContext.Builder(params).withOptionalRandomSeed(seed).create(Optional.empty()),
                    output);
            rawMending += (int) raw.stream().filter(BlockedEnchantmentGameTests::hasMending).count();
            List<ItemStack> loot = table.getRandomItems(params, seed);
            helper.assertTrue(loot.size() == raw.size(), "The modifier changes the number of items, seed " + seed);
            for (int i = 0; i < loot.size(); i++) {
                helper.assertTrue(loot.get(i).getItem() == raw.get(i).getItem()
                        && loot.get(i).getCount() == raw.get(i).getCount(), "The modifier changes an item, seed " + seed);
                helper.assertFalse(hasMending(loot.get(i)), "The fishing loot has Mending, seed " + seed);
            }
        }
        helper.assertTrue(rawMending > 0, "The raw fishing loot has no Mending: the test proves nothing");
        helper.succeed();
    }

    /** The chest loot does not change (inChestLoot = false): the library of a stronghold still gives Mending. */
    @GameTest(template = "bench", batch = "balance_enchantments")
    public static void chestLootKeepsMending(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.STRONGHOLD_LIBRARY);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(2.5, 1, 2.5)))
                .create(LootContextParamSets.CHEST);
        int mending = 0;
        for (int seed = 1; seed <= ROLLS && mending == 0; seed++) {
            mending += (int) table.getRandomItems(params, seed).stream()
                    .filter(BlockedEnchantmentGameTests::hasMending).count();
        }
        helper.assertTrue(mending > 0, "The chest loot has no Mending");
        helper.succeed();
    }

    private static boolean hasMending(ItemStack stack) {
        return hasEnchantment(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY), Enchantments.MENDING)
                || hasEnchantment(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY),
                        Enchantments.MENDING);
    }

    private static boolean hasEnchantment(ItemEnchantments enchantments, ResourceKey<Enchantment> key) {
        return enchantments.keySet().stream().anyMatch(holder -> holder.is(key));
    }

    private static Holder<Enchantment> enchantment(ServerLevel level, ResourceKey<Enchantment> key) {
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }
}
