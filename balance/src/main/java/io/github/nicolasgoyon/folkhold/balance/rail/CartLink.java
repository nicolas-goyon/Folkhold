package io.github.nicolasgoyon.folkhold.balance.rail;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * The links of one minecart: the UUID of the minecart in front of it and the UUID of the minecart behind it. A
 * minecart has a maximum of one front link and one back link. The two minecarts of a link refer to each other.
 *
 * @param front the minecart in front, or empty
 * @param back  the minecart behind, or empty
 */
public record CartLink(Optional<UUID> front, Optional<UUID> back) {
    /** A minecart with no link. */
    public static final CartLink NONE = new CartLink(Optional.empty(), Optional.empty());

    public static final Codec<CartLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("front").forGetter(CartLink::front),
            UUIDUtil.CODEC.optionalFieldOf("back").forGetter(CartLink::back)
    ).apply(instance, CartLink::new));

    /** Tells if the minecart has no link. */
    public boolean isEmpty() {
        return front.isEmpty() && back.isEmpty();
    }

    /** The same links with another front link. */
    public CartLink withFront(Optional<UUID> newFront) {
        return new CartLink(newFront, back);
    }

    /** The same links with another back link. */
    public CartLink withBack(Optional<UUID> newBack) {
        return new CartLink(front, newBack);
    }

    /**
     * The same links without the links to a minecart.
     *
     * @param partner the UUID of the other minecart
     * @return the links that do not refer to the other minecart
     */
    public CartLink without(UUID partner) {
        return new CartLink(front.filter(id -> !id.equals(partner)), back.filter(id -> !id.equals(partner)));
    }
}
