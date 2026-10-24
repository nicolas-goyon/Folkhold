package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.SerializedName;
import io.github.nicolasgoyon.folkhold.core.sim.garrison.GarrisonTraits;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.entity.EntityType;

/**
 * Role of a unit in a garrison. Each role has an entity type. The level of the village sets the number of units
 * of each role (see {@code garrison} in the level table).
 */
public enum GarrisonRole implements SerializedName {
    GUARD,
    ARCHER,
    CAPTAIN,
    HEALER,
    GOLEM,
    RIDER;

    public EntityType<? extends FactionUnit> entityType() {
        return switch (this) {
            case GUARD -> ModEntities.GUARD.get();
            case ARCHER -> ModEntities.ARCHER.get();
            case CAPTAIN -> ModEntities.CAPTAIN.get();
            case HEALER -> ModEntities.HEALER.get();
            case GOLEM -> ModEntities.FACTION_GOLEM.get();
            case RIDER -> ModEntities.RIDER.get();
        };
    }

    /**
     * The number of units of this role in a full garrison.
     *
     * @param traits the garrison of the level of the village (see {@code garrison} in the level table)
     * @return the number of units
     */
    public int count(GarrisonTraits traits) {
        return traits.count(serializedName());
    }

    @Override
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Reads a role from its serialized name.
     *
     * @param name the serialized name
     * @return the role, or empty for an unknown name
     */
    public static Optional<GarrisonRole> fromSerializedName(String name) {
        return SerializedName.lookup(GarrisonRole.class, name);
    }
}
