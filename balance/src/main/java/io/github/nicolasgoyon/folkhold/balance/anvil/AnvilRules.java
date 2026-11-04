package io.github.nicolasgoyon.folkhold.balance.anvil;

import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * The anvil of the balance mod (section {@code anvil} of the server config): no level cap, no "Too Expensive!", a
 * quadratic enchantment cost and a repair cost with no prior work penalty. See {@link AnvilCost}.
 *
 * <p>The listener runs on the server and on the client: the menu calculates its result on both sides.
 */
public final class AnvilRules {
    /** The parsed lists of the config, or null before the first use after a load of the config. */
    @Nullable
    private static volatile Tables tables;

    private AnvilRules() {
    }

    /**
     * The parsed lists of the section {@code anvil}.
     *
     * @param weights the weights of the enchantments
     * @param caps    the level caps
     * @param bases   the base costs of a repair
     */
    private record Tables(KeyedValues weights, KeyedValues caps, KeyedValues bases) {
    }

    /** Parses the lists of the config again at the next use, after a load of the config. */
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == BalanceConfig.SPEC) {
            tables = null;
        }
    }

    /** Parses the lists of the config again at the next use, after a change of the config. */
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == BalanceConfig.SPEC) {
            tables = null;
        }
    }

    private static Tables tables() {
        Tables current = tables;
        if (current == null) {
            current = new Tables(KeyedValues.parse(BalanceConfig.ANVIL_WEIGHTS.get()),
                    KeyedValues.parse(BalanceConfig.ANVIL_LEVEL_CAPS.get()),
                    KeyedValues.parse(BalanceConfig.ANVIL_REPAIR_BASE_COST.get()));
            tables = current;
        }
        return current;
    }

    /** True if the server config is loaded and the anvil rules are on. */
    public static boolean isEnabled() {
        return BalanceConfig.SPEC.isLoaded() && BalanceConfig.ANVIL_ENABLED.get();
    }

    /**
     * Calculates the result, the cost and the material count of the anvil. Register this listener with
     * {@link net.neoforged.bus.api.EventPriority#LOWEST}: the anvil recipes of other mods set their output first, and
     * the listener does nothing when an output is set. When the anvil has no valid operation, the listener cancels
     * the event: the result slot is then empty.
     */
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!isEnabled() || !event.getOutput().isEmpty()) {
            return;
        }
        Operation operation = calculate(event.getLeft(), event.getRight(), event.getName(), event.getPlayer());
        if (operation == null) {
            event.setCanceled(true);
            return;
        }
        event.setOutput(operation.result());
        event.setCost(operation.cost());
        event.setMaterialCost(operation.materialCount());
    }

    /**
     * The result of an anvil operation.
     *
     * @param result        the output item
     * @param cost          the cost in levels
     * @param materialCount the number of right items that the operation uses; 0 uses the full right stack
     */
    private record Operation(ItemStack result, int cost, int materialCount) {
    }

    /**
     * Calculates an anvil operation with the config values.
     *
     * @param left   the left item
     * @param right  the right item, or an empty stack
     * @param name   the name in the text field, or null
     * @param player the player
     * @return the operation, or null when the anvil has no valid operation
     */
    @Nullable
    private static Operation calculate(ItemStack left, ItemStack right, @Nullable String name, Player player) {
        if (left.isEmpty() || !EnchantmentHelper.canStoreEnchantments(left)) {
            return null;
        }
        ItemStack result = left.copy();
        ItemEnchantments.Mutable enchantments =
                new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(result));
        long cost = 0;
        int materialCount = 0;
        boolean changed = false;
        boolean book = !right.isEmpty() && right.has(DataComponents.STORED_ENCHANTMENTS);
        if (!right.isEmpty()) {
            if (result.isDamageableItem() && result.getItem().isValidRepairItem(left, right)) {
                materialCount = AnvilCost.materialUnits(result.getDamageValue(), result.getMaxDamage(),
                        right.getCount());
                if (materialCount <= 0) {
                    return null;
                }
                for (int unit = 0; unit < materialCount; unit++) {
                    result.setDamageValue(result.getDamageValue()
                            - Math.min(result.getDamageValue(), result.getMaxDamage() / AnvilCost.UNITS_FOR_FULL_REPAIR));
                }
                cost += AnvilCost.repairCost(fullRepairCost(left), materialCount);
                changed = true;
            } else {
                if (!book && (!result.is(right.getItem()) || !result.isDamageableItem())) {
                    return null;
                }
                if (result.isDamageableItem() && !book && result.getDamageValue() > 0) {
                    // The vanilla durability of two combined items: the sum of the two, plus 12 % of the maximum.
                    int durability = (left.getMaxDamage() - left.getDamageValue())
                            + (right.getMaxDamage() - right.getDamageValue()) + result.getMaxDamage() * 12 / 100;
                    int damage = Math.max(0, result.getMaxDamage() - durability);
                    if (damage < result.getDamageValue()) {
                        int repaired = result.getDamageValue() - damage;
                        result.setDamageValue(damage);
                        cost += AnvilCost.repairCost(fullRepairCost(left),
                                AnvilCost.equivalentUnits(repaired, result.getMaxDamage()));
                        changed = true;
                    }
                }
                EnchantmentMerge merge = mergeEnchantments(left, right, enchantments, player);
                if (merge == null) {
                    return null;
                }
                cost += merge.cost();
                changed |= merge.changed();
            }
        }
        if (name != null && !StringUtil.isBlank(name)) {
            if (!name.equals(left.getHoverName().getString())) {
                result.set(DataComponents.CUSTOM_NAME, Component.literal(name));
                cost++;
                changed = true;
            }
        } else if (left.has(DataComponents.CUSTOM_NAME)) {
            result.remove(DataComponents.CUSTOM_NAME);
            cost++;
            changed = true;
        }
        if (!changed || (book && !result.isBookEnchantable(right))) {
            return null;
        }
        int penalty = 0;
        if (BalanceConfig.ANVIL_PRIOR_WORK_PENALTY.get()) {
            int leftPenalty = left.getOrDefault(DataComponents.REPAIR_COST, 0);
            int rightPenalty = right.getOrDefault(DataComponents.REPAIR_COST, 0);
            cost += (long) leftPenalty + rightPenalty;
            penalty = right.isEmpty() ? leftPenalty
                    : AnvilMenu.calculateIncreasedRepairCost(Math.max(leftPenalty, rightPenalty));
        }
        if (cost <= 0 || cost > AnvilCost.MAX_COST) {
            return null;
        }
        result.set(DataComponents.REPAIR_COST, penalty);
        EnchantmentHelper.setEnchantments(result, enchantments.toImmutable());
        return new Operation(result, (int) cost, materialCount);
    }

    /**
     * The cost and the change of a merge of enchantments.
     *
     * @param cost    the cost in levels
     * @param changed true if a level of the result is higher than on the left item
     */
    private record EnchantmentMerge(long cost, boolean changed) {
    }

    /**
     * Merges the enchantments of the right item into the enchantments of the result.
     *
     * @return the merge, or null when the left item accepts none of the enchantments, or when the left stack has more
     *         than one item
     */
    @Nullable
    private static EnchantmentMerge mergeEnchantments(ItemStack left, ItemStack right,
            ItemEnchantments.Mutable enchantments, Player player) {
        MergeRule rule = BalanceConfig.ANVIL_MERGE_RULE.get();
        KeyedValues caps = tables().caps();
        KeyedValues weights = tables().weights();
        int absoluteMax = BalanceConfig.ANVIL_ABSOLUTE_MAX_LEVEL.get();
        long increase = 0;
        long incompatible = 0;
        boolean applied = false;
        boolean rejected = false;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry
                : EnchantmentHelper.getEnchantmentsForCrafting(right).entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int from = enchantments.getLevel(enchantment);
            boolean supported = left.supportsEnchantment(enchantment) || player.getAbilities().instabuild;
            for (Holder<Enchantment> present : enchantments.keySet()) {
                if (!present.equals(enchantment) && !Enchantment.areCompatible(enchantment, present)) {
                    supported = false;
                    incompatible++;
                }
            }
            if (!supported) {
                rejected = true;
                continue;
            }
            applied = true;
            int cap = Math.min(absoluteMax, value(caps, enchantment, Integer.MAX_VALUE));
            int to = Math.max(from, Math.min(cap, rule.merge(from, entry.getIntValue())));
            enchantments.set(enchantment, to);
            increase += AnvilCost.levelIncrease(value(weights, enchantment, enchantment.value().getAnvilCost()), from,
                    to);
        }
        if (rejected && !applied) {
            return null;
        }
        if (applied && left.getCount() > 1) {
            // The vanilla anvil refuses to enchant a stack: the result would enchant all the items of the stack.
            return null;
        }
        boolean changed = increase > 0;
        long cost = incompatible + (changed ? AnvilCost.enchantmentCost(BalanceConfig.ANVIL_OPERATION_COST.get(),
                BalanceConfig.ANVIL_ENCHANT_COST_FACTOR.get(), increase) : 0);
        return new EnchantmentMerge(cost, changed);
    }

    /** The cost of a full repair of an item, with its current enchantments. */
    private static long fullRepairCost(ItemStack item) {
        KeyedValues weights = tables().weights();
        long weightedLevels = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry
                : EnchantmentHelper.getEnchantmentsForCrafting(item).entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            if (!enchantment.is(EnchantmentTags.CURSE)) {
                weightedLevels += (long) value(weights, enchantment, enchantment.value().getAnvilCost())
                        * entry.getIntValue();
            }
        }
        return AnvilCost.fullRepairCost(repairBase(item), BalanceConfig.ANVIL_REPAIR_ENCHANT_FACTOR.get(),
                weightedLevels);
    }

    /**
     * The base cost of a repair: the value of the first entry of {@code repairBaseCost} with an item that repairs the
     * item, or {@code repairDefaultBaseCost}.
     */
    private static int repairBase(ItemStack item) {
        KeyedValues bases = tables().bases();
        for (Map.Entry<String, Integer> entry : bases.ids().entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)
                    && item.getItem().isValidRepairItem(item, new ItemStack(BuiltInRegistries.ITEM.get(id)))) {
                return entry.getValue();
            }
        }
        for (Map.Entry<String, Integer> entry : bases.tags().entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) {
                continue;
            }
            for (Holder<Item> material : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id))) {
                if (item.getItem().isValidRepairItem(item, new ItemStack(material))) {
                    return entry.getValue();
                }
            }
        }
        return BalanceConfig.ANVIL_REPAIR_DEFAULT_BASE_COST.get();
    }

    /** The value of an enchantment in a table: by id first, then by the first tag that has the enchantment. */
    private static int value(KeyedValues values, Holder<Enchantment> enchantment, int fallback) {
        String id = enchantment.unwrapKey().map(key -> key.location().toString()).orElse("");
        Integer byId = values.ids().get(id);
        if (byId != null) {
            return byId;
        }
        for (Map.Entry<String, Integer> entry : values.tags().entrySet()) {
            ResourceLocation tag = ResourceLocation.tryParse(entry.getKey());
            if (tag != null && enchantment.is(TagKey.create(Registries.ENCHANTMENT, tag))) {
                return entry.getValue();
            }
        }
        return fallback;
    }
}
