package io.github.nicolasgoyon.folkhold.core.sim.group;

import io.github.nicolasgoyon.folkhold.core.sim.SerializedName;
import java.util.Optional;

/** The role of a member in a mobile group. */
public enum MemberRole implements SerializedName {
    /** The leader of the group. When the merchant dies, the group is lost. */
    MERCHANT("merchant"),
    /** An animal that carries a part of the cargo. */
    PACK_ANIMAL("pack_animal"),
    /** A unit that defends the group. */
    GUARD("guard");

    private final String serializedName;

    MemberRole(String serializedName) {
        this.serializedName = serializedName;
    }

    /** The name in the saved data. */
    @Override
    public String serializedName() {
        return serializedName;
    }

    /**
     * The role with a serialized name.
     *
     * @param name the name in the saved data
     * @return the role, or empty for an unknown name
     */
    public static Optional<MemberRole> fromSerializedName(String name) {
        return SerializedName.lookup(MemberRole.class, name);
    }
}
