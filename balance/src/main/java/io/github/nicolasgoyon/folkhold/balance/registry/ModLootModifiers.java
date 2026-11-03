package io.github.nicolasgoyon.folkhold.balance.registry;

import com.mojang.serialization.MapCodec;
import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.enchantment.BlockedEnchantmentsLootModifier;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Global loot modifiers of the balance mod. The file {@code data/neoforge/loot_modifiers/global_loot_modifiers.json}
 * enables them.
 */
public final class ModLootModifiers {
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, FolkholdBalance.MOD_ID);

    /** Removes the blocked enchantments from the loot of fishing and, as an option, of chests. */
    public static final Supplier<MapCodec<BlockedEnchantmentsLootModifier>> BLOCKED_ENCHANTMENTS =
            SERIALIZERS.register("blocked_enchantments", () -> BlockedEnchantmentsLootModifier.CODEC);

    private ModLootModifiers() {
    }

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
