package io.github.nicolasgoyon.folkhold.world.settlement;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/**
 * The owners of the territory cells in the Overworld, from the claims of the settlements (see
 * {@link SettlementData#claims()}).
 */
public final class Territories {

    private Territories() {
    }

    /** The settlement that owns the cell of a position, or empty if the cell is unclaimed. */
    public static Optional<Settlement> settlementAt(MinecraftServer server, BlockPos pos) {
        SettlementData data = SettlementData.get(server);
        return data.claims().ownerAt(pos.getX(), pos.getZ()).flatMap(id -> data.registry().byId(id));
    }

    /** The faction that owns the cell of a position, or empty if the cell is unclaimed. */
    public static Optional<FactionSpec> factionAt(MinecraftServer server, BlockPos pos) {
        return settlementAt(server, pos).flatMap(settlement -> FactionData.get(server).byId(settlement.factionId()));
    }
}
