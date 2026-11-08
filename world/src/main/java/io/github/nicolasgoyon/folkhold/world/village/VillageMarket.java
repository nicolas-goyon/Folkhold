package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The trade place of a village: its market square, or its plaza.
 *
 * <ul>
 *   <li>From the level 5, the growth builds one market on a free plot (see {@link VillageGrowth}). The market comes
 *       before a new home.</li>
 *   <li>A village with a market trades at the market. The market is the first market sign of the scan of the heart
 *       (see {@link VillageLayout#markets()}).</li>
 *   <li>A hamlet (below the level 5) trades at the plaza. A village of the level 5 or more with no market also trades
 *       at the plaza until its market is built. With no free plot, the village has no market until a plot is free
 *       again (the state {@link State#NO_SPACE}).</li>
 * </ul>
 *
 * <p>The area of the market is the box of the market piece: the box of the construction site that built it, or the box
 * of the piece of the village structure. Else, it is a box of {@link #HALF_SIZE} blocks around the market post. The
 * area of the plaza is the box of the plaza piece of the village structure. Else, it is a box of {@link #HALF_SIZE}
 * blocks around the heart: the heart is in the middle of the plaza.
 */
public final class VillageMarket {
    /** Half the horizontal size of a default area, in blocks: the plaza and the market are 15 blocks long. */
    static final int HALF_SIZE = 7;
    /** A default area starts this number of blocks below its point: the floor of the plaza or of the market. */
    private static final int BELOW = 1;
    /** A default area stops this number of blocks above its point. */
    private static final int ABOVE = 4;
    /** The name of the plaza piece of each variant. */
    private static final String PLAZA = "/plaza";

    private VillageMarket() {
    }

    /** The state of the market of a village. */
    public enum State {
        /** The level of the village needs no market: the village trades at the plaza. */
        HAMLET,
        /**
         * The level needs a market and the village has none. The growth builds it at its next check, when the setting
         * {@code construction.growth} is on and the village has builders and no other site.
         */
        MISSING,
        /** The builders build the market. */
        BUILDING,
        /**
         * The level needs a market and no free plot can hold it: the village trades at the plaza. The last check of the
         * growth examined all the plots: a check with a plot in a chunk that is not ready keeps the last known state.
         */
        NO_SPACE,
        /** The village has a market. */
        BUILT
    }

    /**
     * The trade place of a village.
     *
     * @param market true for the market square, false for the plaza
     * @param point  the market sign of the market, or the heart for the plaza
     * @param area   the box of the market piece or of the plaza
     */
    public record TradePlace(boolean market, BlockPos point, BoundingBox area) {
        public TradePlace {
            Objects.requireNonNull(point, "point");
            Objects.requireNonNull(area, "area");
        }
    }

    /**
     * The state of the market of a village.
     *
     * @param level the level
     * @param heart the village heart
     * @return the state
     */
    public static State state(ServerLevel level, VillageHeartBlockEntity heart) {
        if (!heart.getLayout().markets().isEmpty()) {
            return State.BUILT;
        }
        int villageLevel = Settlements.of(level.getServer(), heart).map(Settlement::level).orElse(1);
        if (!VillageBuildings.needsMarket(VillageBuildingTable.get(), villageLevel, heart.getLayout().buildings())) {
            return State.HAMLET;
        }
        boolean building = BuilderSites.of(level, heart.getBlockPos()).stream()
                .anyMatch(site -> site.template().getPath().endsWith("/" + VillageBuildings.MARKET));
        if (building) {
            return State.BUILDING;
        }
        return heart.isMarketNoSpace() ? State.NO_SPACE : State.MISSING;
    }

    /**
     * The trade place of a village: its market, or its plaza with no market.
     *
     * @param level the level
     * @param heart the village heart
     * @return the trade place
     */
    public static TradePlace tradePlace(ServerLevel level, VillageHeartBlockEntity heart) {
        BlockPos heartPos = heart.getBlockPos();
        Optional<VillageReference> reference = heart.repairReference(level);
        if (!heart.getLayout().markets().isEmpty()) {
            BlockPos sign = heart.getLayout().markets().get(0);
            BoundingBox area = VillageRepairData.get(level).constructionAt(heartPos, sign)
                    .or(() -> reference.flatMap(found -> pieceAt(level, found, sign, "/" + VillageBuildings.MARKET)))
                    .orElseGet(() -> around(postOf(level, sign)));
            return new TradePlace(true, sign, area);
        }
        BoundingBox plaza = reference.flatMap(found -> pieceAt(level, found, heartPos, PLAZA))
                .orElseGet(() -> around(heartPos));
        return new TradePlace(false, heartPos, plaza);
    }

    /** The box of the first piece of the village structure with a name that holds a position. */
    private static Optional<BoundingBox> pieceAt(ServerLevel level, VillageReference reference, BlockPos pos,
            String name) {
        for (VillageReference.Piece piece : reference.pieces()) {
            if (!piece.template().getPath().endsWith(name)) {
                continue;
            }
            Optional<BoundingBox> box = level.getStructureManager().get(piece.template())
                    .map(found -> GhostPlacement.box(found.getSize(), piece.rotation(), Mirror.NONE, piece.origin()))
                    .filter(found -> found.isInside(pos));
            if (box.isPresent()) {
                return box;
            }
        }
        return Optional.empty();
    }

    /** The market post behind a market sign, or the sign when its block is not a market sign any more. */
    private static BlockPos postOf(ServerLevel level, BlockPos sign) {
        BlockState state = level.getBlockState(sign);
        return state.hasProperty(HorizontalDirectionalBlock.FACING) && VillageLayout.isMarket(level, sign, state)
                ? sign.relative(state.getValue(HorizontalDirectionalBlock.FACING).getOpposite())
                : sign;
    }

    /** The default area around a point. */
    static BoundingBox around(BlockPos point) {
        return new BoundingBox(point.getX() - HALF_SIZE, point.getY() - BELOW, point.getZ() - HALF_SIZE,
                point.getX() + HALF_SIZE, point.getY() + ABOVE, point.getZ() + HALF_SIZE);
    }
}
