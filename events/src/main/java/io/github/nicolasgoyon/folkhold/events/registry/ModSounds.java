package io.github.nicolasgoyon.folkhold.events.registry;

import io.github.nicolasgoyon.folkhold.events.FolkholdEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sound events of the events mod. The file {@code assets/folkhold_events/sounds.json} gives their sounds and their
 * subtitles. The rumble of an earthquake is the vanilla thunder with a low pitch.
 */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, FolkholdEvents.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> EARTHQUAKE_RUMBLE = register("event.earthquake.rumble");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(FolkholdEvents.MOD_ID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
