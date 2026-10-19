package io.github.nicolasgoyon.folkhold.world.registry;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sound events of the world mod. The file {@code assets/folkhold_world/sounds.json} gives their sounds and their
 * subtitles. The sounds of the faction golem are the sounds of the vanilla iron golem, with the subtitles of the faction
 * golem.
 */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, FolkholdWorld.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> FACTION_GOLEM_ATTACK =
            register("entity.faction_golem.attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> FACTION_GOLEM_DAMAGE =
            register("entity.faction_golem.damage");
    public static final DeferredHolder<SoundEvent, SoundEvent> FACTION_GOLEM_DEATH =
            register("entity.faction_golem.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> FACTION_GOLEM_HURT =
            register("entity.faction_golem.hurt");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
