package io.github.nicolasgoyon.folkhold.world.admin;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.journal.TerritoryMaps;
import io.github.nicolasgoyon.folkhold.world.network.AdminMapPayload;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/**
 * The map of the admin screen: all the settlements with their territories, the caravans and the traveling merchants,
 * with their positions. The operator selects a mark and teleports to it.
 *
 * <ul>
 *   <li>A settlement has the color of its faction. A settlement that exists only in the simulation and a ruin have
 *       their own marks.</li>
 *   <li>A caravan or a traveling merchant is at its position on its route, with its journey as its name.</li>
 * </ul>
 */
public final class AdminMap {
    /** The color of a settlement with no faction, as 0xRRGGBB. */
    private static final int NO_FACTION = 0x808080;
    private static final int SHORT_ID = 8;

    private AdminMap() {
    }

    /**
     * Builds the map.
     *
     * @param server the server
     * @return the payload of the map
     */
    public static AdminMapPayload build(MinecraftServer server) {
        SettlementData settlements = SettlementData.get(server);
        FactionData factions = FactionData.get(server);
        List<TerritoryMapPayload.Village> territories = new ArrayList<>();
        for (Claimant claimant : settlements.claimants()) {
            settlements.registry().byId(claimant.id())
                    .ifPresent(settlement -> territories.add(TerritoryMaps.village(claimant, settlement, factions)));
        }
        List<AdminMapPayload.Mark> marks = new ArrayList<>();
        for (Settlement settlement : settlements.registry().all()) {
            int kind = !settlement.isActive() ? AdminMapPayload.RUIN
                    : settlement.hasHeart() ? AdminMapPayload.VILLAGE : AdminMapPayload.SIMULATED;
            int color = factions.byId(settlement.factionId()).map(FactionSpec::color).orElse(NO_FACTION);
            marks.add(new AdminMapPayload.Mark(settlement.id(), settlement.name(), kind, settlement.x(),
                    settlement.z(), color & 0xFFFFFF));
        }
        for (MobileGroup group : MobileGroupData.get(server).groups().all()) {
            Route.Position position = group.position();
            String journey = shortId(group.id()) + " " + name(settlements, group.originId()) + " → "
                    + name(settlements, group.destinationId());
            int kind = group.kind() == GroupKind.TRAVELING_MERCHANT ? AdminMapPayload.MERCHANT
                    : AdminMapPayload.CARAVAN;
            marks.add(new AdminMapPayload.Mark(group.id(), journey, kind, (int) Math.round(position.x()),
                    (int) Math.round(position.z()), 0));
        }
        return new AdminMapPayload(new TerritoryMapPayload(territories, List.of()), marks);
    }

    private static String name(SettlementData settlements, String id) {
        Optional<Settlement> settlement = settlements.registry().byId(id);
        return settlement.map(Settlement::name).orElse("?");
    }

    private static String shortId(String id) {
        return id.length() <= SHORT_ID ? id : id.substring(0, SHORT_ID);
    }
}
