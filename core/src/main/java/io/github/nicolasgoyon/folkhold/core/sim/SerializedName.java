package io.github.nicolasgoyon.folkhold.core.sim;

import java.util.Optional;

/**
 * A constant with a stable name in the saved data, the network payloads and the translation keys. The name does
 * not change when the constant is renamed or moved.
 */
public interface SerializedName {
    /** The stable name: lower case, with underscores. */
    String serializedName();

    /**
     * Gives the constant of a type with a serialized name.
     *
     * @param type the enum type
     * @param name the serialized name
     * @param <E>  the enum type
     * @return the constant, or empty for an unknown name. The caller logs the unknown name and chooses a constant.
     */
    static <E extends Enum<E> & SerializedName> Optional<E> lookup(Class<E> type, String name) {
        for (E constant : type.getEnumConstants()) {
            if (constant.serializedName().equals(name)) {
                return Optional.of(constant);
            }
        }
        return Optional.empty();
    }
}
