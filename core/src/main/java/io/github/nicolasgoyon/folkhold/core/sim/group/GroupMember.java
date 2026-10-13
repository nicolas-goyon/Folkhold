package io.github.nicolasgoyon.folkhold.core.sim.group;

import java.util.Objects;
import java.util.UUID;

/**
 * One member of a mobile group. The entity of the member always has the UUID of the member.
 */
public final class GroupMember {
    private final UUID id;
    private final MemberRole role;
    private float health;

    /**
     * Makes a member.
     *
     * @param id     the stable UUID of the member and of its entity
     * @param role   the role in the group
     * @param health the health points. A value of 0 or less means that the member is dead.
     */
    public GroupMember(UUID id, MemberRole role, float health) {
        this.id = Objects.requireNonNull(id);
        this.role = Objects.requireNonNull(role);
        this.health = Math.max(0.0F, health);
    }

    public UUID id() {
        return id;
    }

    public MemberRole role() {
        return role;
    }

    /** The health points. 0 if the member is dead. */
    public float health() {
        return health;
    }

    /**
     * Sets the health points.
     *
     * @param health the health points. A value of 0 or less means that the member is dead.
     */
    public void setHealth(float health) {
        this.health = Math.max(0.0F, health);
    }

    /** True if the health is above 0. */
    public boolean isAlive() {
        return health > 0.0F;
    }
}
