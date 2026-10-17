package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import java.util.Locale;
import net.minecraft.world.entity.EntityType;

/**
 * Role of a unit in a garrison. Each role has an entity type and a configured count.
 */
public enum GarrisonRole {
    GUARD,
    ARCHER,
    CAPTAIN;

    public EntityType<? extends FactionUnit> entityType() {
        return switch (this) {
            case GUARD -> ModEntities.GUARD.get();
            case ARCHER -> ModEntities.ARCHER.get();
            case CAPTAIN -> ModEntities.CAPTAIN.get();
        };
    }

    /** Number of units of this role in a full garrison, from the config. */
    public int configuredCount() {
        return switch (this) {
            case GUARD -> WorldConfig.GARRISON_GUARDS.get();
            case ARCHER -> WorldConfig.GARRISON_ARCHERS.get();
            case CAPTAIN -> WorldConfig.GARRISON_CAPTAINS.get();
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Reads a role from its serialized name.
     *
     * @param name the serialized name
     * @return the role, or null if the name is unknown
     */
    public static GarrisonRole fromSerializedName(String name) {
        for (GarrisonRole role : values()) {
            if (role.serializedName().equals(name)) {
                return role;
            }
        }
        return null;
    }
}
