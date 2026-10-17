package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.Map;
import java.util.UUID;

/**
 * Makes the caravans of the trade between villages.
 *
 * <ul>
 *   <li>A caravan has one merchant, 1 to 3 mules and 2 to 4 guards.</li>
 *   <li>One mule carries 15 stacks of the cargo.</li>
 *   <li>The level of the origin settlement sets the number of guards: 2, 3 from level 8, 4 from level 16.</li>
 *   <li>The caravan has the faction of its origin settlement.</li>
 * </ul>
 */
public final class Caravans {
    /** Health of the merchant: the health of a villager. */
    public static final float MERCHANT_HEALTH = 20.0F;
    /** Health of a mule (the middle of the vanilla range). */
    public static final float MULE_HEALTH = 22.0F;
    /** Stacks of cargo that one mule carries: the slots of a mule chest. */
    public static final int STACKS_PER_MULE = 15;
    /** Items in one stack of cargo. */
    public static final int STACK_SIZE = 64;
    /** The maximum number of mules. */
    public static final int MAX_MULES = 3;

    private Caravans() {
    }

    /**
     * Makes a caravan in the state waiting. It leaves at its departure tick.
     *
     * @param origin        the settlement that sells
     * @param destination   the settlement that buys
     * @param route         the route from the origin to the destination
     * @param cargo         the goods, by item
     * @param payment       the emeralds that the destination pays at the arrival
     * @param departureTick the number of the abstract tick when the caravan leaves
     * @return the caravan
     */
    public static MobileGroup create(Settlement origin, Settlement destination, Route route,
            Map<String, Integer> cargo, long payment, long departureTick) {
        MobileGroup caravan = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN, origin.factionId(),
                origin.id(), destination.id(), route);
        cargo.forEach((item, count) -> caravan.cargo().set(item, count));
        caravan.setPayment(payment);
        caravan.setDepartureTick(departureTick);
        caravan.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, MERCHANT_HEALTH));
        for (int i = 0; i < mules(cargo); i++) {
            caravan.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, MULE_HEALTH));
        }
        float guardHealth = (float) WorldConfig.GUARD.health();
        for (int i = 0; i < guards(origin.level()); i++) {
            caravan.addMember(new GroupMember(UUID.randomUUID(), MemberRole.GUARD, guardHealth));
        }
        return caravan;
    }

    /** The number of mules for a cargo: 1 to 3. */
    public static int mules(Map<String, Integer> cargo) {
        int stacks = 0;
        for (int count : cargo.values()) {
            stacks += (count + STACK_SIZE - 1) / STACK_SIZE;
        }
        return Math.max(1, Math.min(MAX_MULES, (stacks + STACKS_PER_MULE - 1) / STACKS_PER_MULE));
    }

    /** The number of guards for the level of the origin settlement: 2 to 4. */
    public static int guards(int level) {
        return Math.min(4, 2 + level / 8);
    }
}
