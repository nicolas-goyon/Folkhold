package io.github.nicolasgoyon.folkhold.balance.enchantment;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Replaces each blocked enchantment in the loot of fishing (config {@code inFishing}) and of chests (config
 * {@code inChestLoot}) with a random enchantment of the tag {@code #minecraft:on_random_loot} (picked with the weights of the enchantments), at
 * the same level in
 * the range of the new enchantment. The modifier applies to enchanted items and to enchanted books.
 */
public final class BlockedEnchantmentsLootModifier extends LootModifier {
    public static final MapCodec<BlockedEnchantmentsLootModifier> CODEC = RecordCodecBuilder.mapCodec(
            instance -> codecStart(instance).apply(instance, BlockedEnchantmentsLootModifier::new));

    public BlockedEnchantmentsLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation table = context.getQueriedLootTableId();
        if (!BalanceConfig.SPEC.isLoaded() || !appliesTo(LootScope.of(table.getNamespace(), table.getPath()))) {
            return generatedLoot;
        }
        EnchantmentFilter filter = BlockedEnchantments.filter();
        for (ItemStack stack : generatedLoot) {
            BlockedEnchantments.replaceBlocked(stack, filter, EnchantmentTags.ON_RANDOM_LOOT, true,
                    context.getLevel().registryAccess(), context.getRandom());
        }
        return generatedLoot;
    }

    private static boolean appliesTo(LootScope scope) {
        return switch (scope) {
            case FISHING -> BalanceConfig.BLOCKED_IN_FISHING.get();
            case CHEST -> BalanceConfig.BLOCKED_IN_CHEST_LOOT.get();
            case OTHER -> false;
        };
    }

    @Override
    public MapCodec<BlockedEnchantmentsLootModifier> codec() {
        return CODEC;
    }
}
