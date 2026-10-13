package io.github.nicolasgoyon.folkhold.core.sim.group;

/** The role of a member in a mobile group. */
public enum MemberRole {
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
    public String serializedName() {
        return serializedName;
    }

    /**
     * The role with a name.
     *
     * @param name the name in the saved data
     * @return the role. An unknown name gives {@link #GUARD}.
     */
    public static MemberRole fromSerializedName(String name) {
        for (MemberRole role : values()) {
            if (role.serializedName.equals(name)) {
                return role;
            }
        }
        return GUARD;
    }
}
