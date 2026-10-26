package io.github.nicolasgoyon.folkhold.world.village.structure;

import io.github.nicolasgoyon.folkhold.core.sim.village.PalisadeHeights;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Box;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Cell;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Gate;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Segment;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Side;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Tower;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The wall of a fortified village: the pieces of the wall around the pieces of the pools (see {@link WallLayout}).
 *
 * <ul>
 *   <li>A gate goes on each side, on the axis of the start piece: the streets go out of the start piece on these
 *       axes.</li>
 *   <li>The structure reads the height of the terrain on the line of the palisade, and at 2 blocks inside and outside
 *       the line. The bases of the columns come from these heights (see {@link PalisadeHeights}).</li>
 *   <li>A tower or a gate is a structure template on the highest base under it. The bases near it go up again, so
 *       that the palisade has no high step next to it.</li>
 *   <li>Each segment of palisade is a {@link PalisadePiece}. A {@link VillagePathPiece} goes from each gate to the
 *       first piece of the pools on the axis.</li>
 * </ul>
 */
public final class VillageWall {
    /** The terrain of the near blocks is at this distance from the line, inside and outside. */
    private static final int NEAR_DISTANCE = 2;
    /** The near blocks are in a window of this number of blocks before and after a block of the line. */
    private static final int NEAR_RADIUS = 1;
    /** The ground level of a template is 1 block above its origin: the first layer above the ground. */
    private static final int GROUND_LEVEL_DELTA = 1;

    private VillageWall() {
    }

    /**
     * The pieces of the wall around the pieces of a village.
     *
     * @param context  the context of the generation
     * @param village  the pieces of the pools
     * @param center   the middle of the start piece
     * @param settings the settings of the wall
     * @return the pieces of the wall
     */
    public static List<StructurePiece> add(Structure.GenerationContext context, List<StructurePiece> village,
            BlockPos center, WallSettings settings) {
        List<Box> boxes = village.stream().map(piece -> box(piece.getBoundingBox())).toList();
        List<Gate> gates = List.of(new Gate(Side.NORTH, center.getX()), new Gate(Side.EAST, center.getZ()),
                new Gate(Side.SOUTH, center.getX()), new Gate(Side.WEST, center.getZ()));
        WallLayout layout = WallLayout.plan(boxes, settings.margin(), settings.towerSpacing(), gates);
        Terrain terrain = new Terrain(context);
        int[] bases = bases(layout, terrain);
        StructureTemplateManager templates = context.structureTemplateManager();
        List<StructurePiece> pieces = new ArrayList<>();
        Map<Tower, Integer> towerLevels = new HashMap<>();
        Map<Gate, Integer> gateLevels = new HashMap<>();
        for (Tower tower : layout.towers()) {
            towerLevels.put(tower, flatten(bases, layout.ringIndices(layout.towerBox(tower))));
        }
        for (Gate gate : layout.gates()) {
            gateLevels.put(gate, flatten(bases, layout.ringIndices(gate)));
        }
        int[] smooth = PalisadeHeights.smooth(bases);
        for (Tower tower : layout.towers()) {
            pieces.add(template(templates, settings.tower(), towerOrigin(layout, tower, towerLevels.get(tower)),
                    rotation(tower.side())));
        }
        for (Gate gate : layout.gates()) {
            pieces.add(template(templates, settings.gate(), gateOrigin(layout, gate, gateLevels.get(gate)),
                    rotation(gate.side())));
        }
        for (Segment segment : layout.segments()) {
            pieces.add(palisade(layout, segment, smooth, settings.palisade()));
        }
        for (Gate gate : layout.gates()) {
            path(layout, gate, boxes, terrain).ifPresent(pieces::add);
        }
        return pieces;
    }

    /** The box of a structure piece on the horizontal plane. */
    static Box box(BoundingBox box) {
        return new Box(box.minX(), box.minZ(), box.maxX(), box.maxZ());
    }

    /** The bases of the columns of the ring, from the terrain of the line and of the near blocks. */
    private static int[] bases(WallLayout layout, Terrain terrain) {
        int size = layout.ringSize();
        int[] line = new int[size];
        int[] inside = new int[size];
        int[] outside = new int[size];
        for (int i = 0; i < size; i++) {
            Cell cell = layout.ringCell(i);
            Side side = layout.ringSide(i);
            line[i] = terrain.height(cell.x(), cell.z());
            inside[i] = terrain.height(cell.x() + NEAR_DISTANCE * side.insideX(),
                    cell.z() + NEAR_DISTANCE * side.insideZ());
            outside[i] = terrain.height(cell.x() - NEAR_DISTANCE * side.insideX(),
                    cell.z() - NEAR_DISTANCE * side.insideZ());
        }
        return PalisadeHeights.bases(line, PalisadeHeights.near(NEAR_RADIUS, line, inside, outside));
    }

    /** Gives the highest base of some blocks of the ring to all these blocks, and returns it. */
    private static int flatten(int[] bases, List<Integer> indices) {
        int level = Integer.MIN_VALUE;
        for (int index : indices) {
            level = Math.max(level, bases[index]);
        }
        for (int index : indices) {
            bases[index] = level;
        }
        return level;
    }

    /**
     * The turn of the templates of a side. The outside of the templates is to the north: the north side keeps them,
     * and the other sides turn them clockwise.
     */
    static Rotation rotation(Side side) {
        return switch (side) {
            case NORTH -> Rotation.NONE;
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
        };
    }

    /** The origin of the template of a tower: the corner of the tower on the line, at the start of the tower. */
    static BlockPos towerOrigin(WallLayout layout, Tower tower, int level) {
        Cell start = layout.cell(tower.side(), tower.along());
        return new BlockPos(start.x(), level, start.z());
    }

    /**
     * The origin of the template of a gate. The template is {@link WallLayout#GATE} blocks wide, and its first row is
     * outside the line.
     */
    static BlockPos gateOrigin(WallLayout layout, Gate gate, int level) {
        Side side = gate.side();
        Cell start = layout.cell(side, layout.along(gate) - WallLayout.GATE / 2);
        return new BlockPos(start.x() - side.insideX(), level, start.z() - side.insideZ());
    }

    /** A structure piece of a template, on the terrain (projection {@code rigid}). */
    private static PoolElementStructurePiece template(StructureTemplateManager templates, ResourceLocation id,
            BlockPos origin, Rotation rotation) {
        StructurePoolElement element = StructurePoolElement.single(id.toString())
                .apply(StructureTemplatePool.Projection.RIGID);
        return new PoolElementStructurePiece(templates, element, origin, GROUND_LEVEL_DELTA, rotation,
                element.getBoundingBox(templates, origin, rotation), LiquidSettings.APPLY_WATERLOGGING);
    }

    /** The piece of a segment of palisade. The columns go along the side, in the direction of the ring. */
    private static PalisadePiece palisade(WallLayout layout, Segment segment, int[] bases, PalisadeBlocks blocks) {
        Side side = segment.side();
        Cell start = layout.cell(side, segment.from());
        Cell next = layout.cell(side, segment.from() + 1);
        int[] columns = new int[segment.length()];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = bases[layout.ringIndex(side, segment.from() + i)];
        }
        return new PalisadePiece(start.x(), start.z(), next.x() - start.x(), next.z() - start.z(), columns, blocks);
    }

    /**
     * The path from a gate to the first piece of the pools on its axis. The path starts after the inner row of the
     * gate. Empty if the gate is next to a piece.
     */
    private static Optional<VillagePathPiece> path(WallLayout layout, Gate gate, List<Box> boxes,
            Terrain terrain) {
        Side side = gate.side();
        Cell middle = layout.cell(side, layout.along(gate));
        int startX = middle.x() + 2 * side.insideX();
        int startZ = middle.z() + 2 * side.insideZ();
        Box outline = layout.outline();
        int length = 0;
        while (true) {
            int x = startX + length * side.insideX();
            int z = startZ + length * side.insideZ();
            if (!outline.contains(x, z) || boxes.stream().anyMatch(box -> box.contains(x, z))) {
                break;
            }
            length++;
        }
        if (length == 0) {
            return Optional.empty();
        }
        int endX = startX + (length - 1) * side.insideX();
        int endZ = startZ + (length - 1) * side.insideZ();
        int low = Math.min(terrain.height(startX, startZ), terrain.height(endX, endZ));
        int high = Math.max(terrain.height(startX, startZ), terrain.height(endX, endZ));
        return Optional.of(new VillagePathPiece(startX, startZ, side.insideX(), side.insideZ(), length,
                low - VillagePathPiece.SEARCH, high + VillagePathPiece.SEARCH));
    }

    /** The height of the terrain before the generation of the chunks: the Y of the top block. */
    private static final class Terrain {
        private final Structure.GenerationContext context;
        private final Map<Long, Integer> heights = new HashMap<>();

        Terrain(Structure.GenerationContext context) {
            this.context = context;
        }

        int height(int x, int z) {
            return heights.computeIfAbsent(BlockPos.asLong(x, 0, z),
                    key -> context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                            context.heightAccessor(), context.randomState()) - 1);
        }
    }
}
