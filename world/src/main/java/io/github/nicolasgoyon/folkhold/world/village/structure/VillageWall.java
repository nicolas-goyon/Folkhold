package io.github.nicolasgoyon.folkhold.world.village.structure;

import io.github.nicolasgoyon.folkhold.core.sim.village.HeightGrid;
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
 * The wall of a fortified village: the pieces of the wall around the pieces of the pools, and around the space of a
 * home on each free plot (see {@link WallLayout}).
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
 *   <li>{@link TerrainPiece}s make the terrain flat near the wall: along the palisade, in sections of
 *       {@link #SECTION} columns at their highest base, and in front of the gates, at the level of the gate.</li>
 * </ul>
 */
public final class VillageWall {
    /** The terrain of the near blocks is at this distance from the line, inside and outside. */
    private static final int NEAR_DISTANCE = 2;
    /** The near blocks are in a window of this number of blocks before and after a block of the line. */
    private static final int NEAR_RADIUS = 1;
    /** The ground level of a template is 1 block above its origin: the first layer above the ground. */
    private static final int GROUND_LEVEL_DELTA = 1;
    /** The terrain is flat along the palisade in sections of this number of columns. */
    static final int SECTION = 2;
    /** The flat terrain of the palisade goes this number of blocks inside and outside the line. */
    static final int FLAT_WIDTH = 2;
    /** The flat terrain in front of a gate goes this number of blocks outside the line. */
    static final int APPROACH = 6;
    /** The flat terrain behind a gate goes this number of blocks inside the line. */
    static final int ENTRANCE = 3;

    private VillageWall() {
    }

    /**
     * The pieces of the wall around the pieces of a village, and around the spaces that the village keeps for its
     * growth.
     *
     * @param context  the context of the generation
     * @param village  the pieces of the pools
     * @param reserved the spaces for later buildings, for example the homes of the free plots
     * @param center   the middle of the start piece
     * @param settings the settings of the wall
     * @return the pieces of the wall
     */
    public static List<StructurePiece> add(Structure.GenerationContext context, List<StructurePiece> village,
            List<BoundingBox> reserved, BlockPos center, WallSettings settings) {
        List<Box> boxes = village.stream().map(piece -> box(piece.getBoundingBox())).toList();
        List<Box> inside = new ArrayList<>(boxes);
        reserved.forEach(space -> inside.add(box(space)));
        List<Gate> gates = List.of(new Gate(Side.NORTH, center.getX()), new Gate(Side.EAST, center.getZ()),
                new Gate(Side.SOUTH, center.getX()), new Gate(Side.WEST, center.getZ()));
        WallLayout layout = WallLayout.plan(inside, settings.margin(), settings.towerSpacing(), gates);
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
            pieces.add(template(templates, settings, settings.tower(),
                    towerOrigin(layout, tower, towerLevels.get(tower)), rotation(tower.side())));
        }
        for (Gate gate : layout.gates()) {
            pieces.add(template(templates, settings, settings.gate(), gateOrigin(layout, gate, gateLevels.get(gate)),
                    rotation(gate.side())));
        }
        for (Segment segment : layout.segments()) {
            pieces.add(palisade(layout, segment, smooth, settings.palisade()));
        }
        for (Gate gate : layout.gates()) {
            path(layout, gate, boxes, terrain).ifPresent(pieces::add);
        }
        pieces.addAll(terrain(layout, smooth, gateLevels));
        return pieces;
    }

    /**
     * The pieces that make the terrain flat near the wall: along each segment of palisade, in sections of
     * {@link #SECTION} columns at their highest base, and in front of each gate and behind it, at the level of the
     * gate.
     *
     * @param layout     the wall
     * @param bases      the bases of the ring
     * @param gateLevels the level of each gate
     * @return the pieces
     */
    static List<TerrainPiece> terrain(WallLayout layout, int[] bases, Map<Gate, Integer> gateLevels) {
        List<TerrainPiece> pieces = new ArrayList<>();
        for (Segment segment : layout.segments()) {
            Side side = segment.side();
            for (int from = segment.from(); from <= segment.to(); from += SECTION) {
                int to = Math.min(from + SECTION - 1, segment.to());
                int ground = Integer.MIN_VALUE;
                for (int along = from; along <= to; along++) {
                    ground = Math.max(ground, bases[layout.ringIndex(side, along)]);
                }
                pieces.add(across(layout.cell(side, from), layout.cell(side, to), side, FLAT_WIDTH, FLAT_WIDTH,
                        ground));
            }
        }
        for (Gate gate : layout.gates()) {
            int middle = layout.along(gate);
            pieces.add(across(layout.cell(gate.side(), middle - WallLayout.GATE / 2),
                    layout.cell(gate.side(), middle + WallLayout.GATE / 2), gate.side(), APPROACH, ENTRANCE,
                    gateLevels.get(gate)));
        }
        return pieces;
    }

    /**
     * A terrain piece across the line of a side, between 2 blocks of the line: it goes a number of blocks outside and
     * a number of blocks inside.
     */
    private static TerrainPiece across(Cell first, Cell last, Side side, int outside, int inside, int ground) {
        int minX = Math.min(first.x(), last.x());
        int maxX = Math.max(first.x(), last.x());
        int minZ = Math.min(first.z(), last.z());
        int maxZ = Math.max(first.z(), last.z());
        int outX = -side.insideX() * outside;
        int outZ = -side.insideZ() * outside;
        int inX = side.insideX() * inside;
        int inZ = side.insideZ() * inside;
        return new TerrainPiece(minX + Math.min(outX, inX), minZ + Math.min(outZ, inZ), maxX + Math.max(outX, inX),
                maxZ + Math.max(outZ, inZ), ground);
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

    /** A structure piece of a template, on the terrain (projection {@code rigid}), with the processors of the wall. */
    private static PoolElementStructurePiece template(StructureTemplateManager templates, WallSettings settings,
            ResourceLocation id, BlockPos origin, Rotation rotation) {
        StructurePoolElement element = StructurePoolElement.single(id.toString(), settings.processors())
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

    /**
     * The height of the terrain before the generation of the chunks: the Y of the top block. The chunk generator
     * calculates the height of each column from the noise of the terrain, and this calculation takes time. Thus the
     * structure reads the heights at the corners of a grid of {@link #CELL} blocks only, and interpolates between the
     * corners (see {@link HeightGrid}). The noise of the vanilla terrain also has cells of 4 blocks: the interpolation
     * stays near the height of the column.
     */
    private static final class Terrain {
        /** The distance between 2 corners of the grid, in blocks. */
        private static final int CELL = 4;

        private final Structure.GenerationContext context;
        private final Map<Long, Integer> corners = new HashMap<>();

        Terrain(Structure.GenerationContext context) {
            this.context = context;
        }

        int height(int x, int z) {
            return HeightGrid.interpolate(x, z, CELL, this::corner);
        }

        private int corner(int x, int z) {
            return corners.computeIfAbsent(BlockPos.asLong(x, 0, z),
                    key -> context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                            context.heightAccessor(), context.randomState()) - 1);
        }
    }
}
