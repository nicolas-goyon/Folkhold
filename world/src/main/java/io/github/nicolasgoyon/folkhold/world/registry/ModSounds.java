package io.github.nicolasgoyon.folkhold.world.registry;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonRole;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
 *
 * <p>The voices of the faction units (see {@link UnitVoice}) have one event for each role and each voice, for example
 * {@code entity.unit.guard.alert}. Their sounds are vanilla sounds of illagers, villagers and players, with a pitch and
 * a volume for each role. The culture of the faction of a unit changes the pitch of its voice (see
 * {@link io.github.nicolasgoyon.folkhold.world.faction.Culture#voicePitch()}). The golem has no voice.
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

    /** A sound of the voice of a faction unit. */
    public enum UnitVoice {
        /** A calm unit, from time to time. */
        AMBIENT,
        /** A unit that gets a target after a calm time. */
        ALERT,
        /** A unit that hits its target, or an archer that shoots. */
        ATTACK,
        /** A unit that gets damage. */
        HURT,
        /** A unit that dies. */
        DEATH;

        /** The last part of the name of the event, for example {@code alert}. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The voices of the roles that have a voice: all the roles except the golem. */
    private static final Map<GarrisonRole, Map<UnitVoice, DeferredHolder<SoundEvent, SoundEvent>>> UNIT_VOICES =
            unitVoices();

    private ModSounds() {
    }

    private static Map<GarrisonRole, Map<UnitVoice, DeferredHolder<SoundEvent, SoundEvent>>> unitVoices() {
        Map<GarrisonRole, Map<UnitVoice, DeferredHolder<SoundEvent, SoundEvent>>> voices =
                new EnumMap<>(GarrisonRole.class);
        for (GarrisonRole role : GarrisonRole.values()) {
            if (role == GarrisonRole.GOLEM) {
                continue;
            }
            Map<UnitVoice, DeferredHolder<SoundEvent, SoundEvent>> events = new EnumMap<>(UnitVoice.class);
            for (UnitVoice voice : UnitVoice.values()) {
                events.put(voice, register("entity.unit." + role.name().toLowerCase(Locale.ROOT) + "." + voice.id()));
            }
            voices.put(role, events);
        }
        return voices;
    }

    /**
     * The sound event of a voice of a unit.
     *
     * @param role  the role of the unit
     * @param voice the voice
     * @return the sound event, or empty for a role with no voice (the golem)
     */
    public static Optional<SoundEvent> unitVoice(GarrisonRole role, UnitVoice voice) {
        Map<UnitVoice, DeferredHolder<SoundEvent, SoundEvent>> events = UNIT_VOICES.get(role);
        return events == null ? Optional.empty() : Optional.of(events.get(voice).get());
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
