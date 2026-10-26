package io.github.nicolasgoyon.folkhold.world.village.structure;

import io.github.nicolasgoyon.folkhold.core.sim.village.HeightGrid;
import io.github.nicolasgoyon.folkhold.core.sim.village.PalisadeHeights;
import io.github.nicolasgoyon.folkhold.core.sim.village.PathRamp;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Box;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Cell;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Gate;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Segment;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Side;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout.Tower;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots.Plot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntBinaryOperator;
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
 *   <li>Each segment of palisade is a {@link PalisadePiece}. A {@link VillagePathPiece} with the width of a street goes
 *       from each gate to the plaza, through the streets on the axis: an arm (see {@link Arm}). The arm is a ramp from
 *       the level of the gate to the level of the plaza (see {@link PathRamp}). The houses on the plots of an arm move
 *       up or down to the level of the arm at their plot.</li>
 *   <li>{@link TerrainPiece}s make the terrain flat near the wall: along the palisade, in sections of
 *       {@link #SECTION} columns at their highest base, and in front of the gates, at the level of the gate. The flat
 *       terrain of a gate also clears its space: no terrain above the level of the gate.</li>
 *   <li>The water side: the top block of the terrain is water at the sea level or lower (see {@link #isWater}). On
 *       water, the palisade stands on its foundation, with no flat terrain along it. A gate that opens on water gets a
 *       {@link PierPiece} in front of it, and no flat terrain there.</li>
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
    /** The points in front of a gate are at these distances outside the line. */
    private static final int[] FRONT = {2, 4, 6};
    /** A gate opens on water when this number of the points in front of it are on water, or more. */
    static final int WATER_FRONT = 2;
    /** The number of blocks on each side of the middle of a street: the plots are at this distance. */
    static final int STREET_HALF_WIDTH = 2;
    /** A pier starts at this distance outside the line: after the outer row of the gate. */
    static final int PIER_START = 2;

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
        int seaLevel = context.chunkGenerator().getSeaLevel();
        int[] bases = bases(layout, terrain);
        boolean[] water = new boolean[layout.ringSize()];
        for (int i = 0; i < water.length; i++) {
            Cell cell = layout.ringCell(i);
            water[i] = isWater(terrain.height(cell.x(), cell.z()), seaLevel);
        }
        Set<Gate> waterGates = new HashSet<>();
        for (Gate gate : layout.gates()) {
            if (isWaterGate(layout, gate, terrain::height, seaLevel)) {
                waterGates.add(gate);
            }
        }
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
        List<Stop> stops = stops(village);
        for (Gate gate : layout.gates()) {
            Optional<Arm> arm = arm(layout, gate, stops);
            if (arm.isPresent()) {
                int[] levels = levels(arm.get(), gateLevels.get(gate), terrain);
                pieces.add(new VillagePathPiece(arm.get().startX(), arm.get().startZ(), arm.get().stepX(),
                        arm.get().stepZ(), levels, true));
                moveHouses(templates, village, arm.get(), levels);
            }
            if (waterGates.contains(gate)) {
                pieces.add(pier(layout, gate, gateLevels.get(gate), terrain::height, seaLevel, settings.pier()));
            }
        }
        pieces.addAll(terrain(layout, smooth, gateLevels, water, waterGates));
        return pieces;
    }

    /**
     * True if the top block of the terrain is water: at the sea level or lower. The heights of the terrain before the
     * generation of the chunks have water at the sea level and below it.
     *
     * @param top      the Y of the top block, water included
     * @param seaLevel the sea level of the chunk generator
     * @return true for water
     */
    static boolean isWater(int top, int seaLevel) {
        return top < seaLevel;
    }

    /**
     * True if a gate opens on water: {@link #WATER_FRONT} of the points in front of it are on water, or more.
     *
     * @param layout   the wall
     * @param gate     the gate
     * @param top      the Y of the top block of the terrain at a position (X, Z), water included
     * @param seaLevel the sea level of the chunk generator
     * @return true for a water gate
     */
    static boolean isWaterGate(WallLayout layout, Gate gate, IntBinaryOperator top, int seaLevel) {
        Side side = gate.side();
        Cell middle = layout.cell(side, layout.along(gate));
        int count = 0;
        for (int distance : FRONT) {
            if (isWater(top.applyAsInt(middle.x() - side.insideX() * distance, middle.z() - side.insideZ() * distance),
                    seaLevel)) {
                count++;
            }
        }
        return count >= WATER_FRONT;
    }

    /**
     * The pier of a water gate. It starts after the outer row of the gate and goes out over the water, to the first
     * position on land. It has {@link PierPiece#MIN_LENGTH} rows at least and {@link PierPiece#MAX_LENGTH} rows at most.
     * The deck is at the level of the gate.
     *
     * @param layout   the wall
     * @param gate     the gate
     * @param level    the level of the gate
     * @param top      the Y of the top block of the terrain at a position (X, Z), water included
     * @param seaLevel the sea level of the chunk generator
     * @param blocks   the blocks of the pier
     * @return the pier
     */
    static PierPiece pier(WallLayout layout, Gate gate, int level, IntBinaryOperator top, int seaLevel,
            PierBlocks blocks) {
        Side side = gate.side();
        Cell middle = layout.cell(side, layout.along(gate));
        int outX = -side.insideX();
        int outZ = -side.insideZ();
        int length = 0;
        while (length < PierPiece.MAX_LENGTH) {
            int distance = PIER_START + length;
            boolean onWater = isWater(top.applyAsInt(middle.x() + outX * distance, middle.z() + outZ * distance),
                    seaLevel);
            if (!onWater && length >= PierPiece.MIN_LENGTH) {
                break;
            }
            length++;
        }
        return new PierPiece(middle.x() + outX * PIER_START, middle.z() + outZ * PIER_START, outX, outZ, length,
                level, blocks);
    }

    /**
     * The pieces that make the terrain flat near the wall: along each segment of palisade, in sections of
     * {@link #SECTION} columns at their highest base, and in front of each gate and behind it, at the level of the
     * gate. On water, the palisade stands on its foundation: a section with all its columns on water gets no terrain
     * piece. A water gate gets the flat terrain behind it only: its pier goes in front of it.
     *
     * @param layout     the wall
     * @param bases      the bases of the ring
     * @param gateLevels the level of each gate
     * @param water      for each position of the ring, true if the line is on water
     * @param waterGates the gates that open on water
     * @return the pieces
     */
    static List<TerrainPiece> terrain(WallLayout layout, int[] bases, Map<Gate, Integer> gateLevels,
            boolean[] water, Set<Gate> waterGates) {
        List<TerrainPiece> pieces = new ArrayList<>();
        for (Segment segment : layout.segments()) {
            Side side = segment.side();
            for (int from = segment.from(); from <= segment.to(); from += SECTION) {
                int to = Math.min(from + SECTION - 1, segment.to());
                int ground = Integer.MIN_VALUE;
                boolean onWater = true;
                for (int along = from; along <= to; along++) {
                    int index = layout.ringIndex(side, along);
                    ground = Math.max(ground, bases[index]);
                    onWater &= water[index];
                }
                if (!onWater) {
                    pieces.add(across(layout.cell(side, from), layout.cell(side, to), side, FLAT_WIDTH, FLAT_WIDTH,
                            ground, false));
                }
            }
        }
        for (Gate gate : layout.gates()) {
            int middle = layout.along(gate);
            pieces.add(across(layout.cell(gate.side(), middle - WallLayout.GATE / 2),
                    layout.cell(gate.side(), middle + WallLayout.GATE / 2), gate.side(),
                    waterGates.contains(gate) ? 0 : APPROACH, ENTRANCE, gateLevels.get(gate), true));
        }
        return pieces;
    }

    /**
     * A terrain piece across the line of a side, between 2 blocks of the line: it goes a number of blocks outside and
     * a number of blocks inside.
     */
    private static TerrainPiece across(Cell first, Cell last, Side side, int outside, int inside, int ground,
            boolean clear) {
        int minX = Math.min(first.x(), last.x());
        int maxX = Math.max(first.x(), last.x());
        int minZ = Math.min(first.z(), last.z());
        int maxZ = Math.max(first.z(), last.z());
        int outX = -side.insideX() * outside;
        int outZ = -side.insideZ() * outside;
        int inX = side.insideX() * inside;
        int inZ = side.insideZ() * inside;
        return new TerrainPiece(minX + Math.min(outX, inX), minZ + Math.min(outZ, inZ), maxX + Math.max(outX, inX),
                maxZ + Math.max(outZ, inZ), ground, clear);
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
     * A piece of the pools that stops an arm: a piece that is not a street.
     *
     * @param box   the box of the piece on the horizontal plane
     * @param level the Y of the top block of the floor of the piece: the lowest layer of its box
     */
    record Stop(Box box, int level) {
    }

    /**
     * An arm of a village: the rows of a straight path from a gate to the plaza, through the streets on the axis of the
     * gate. The arm starts after the inner row of the gate, and stops at the first piece on its axis that is not a
     * street, or at the outline of the wall.
     *
     * @param startX the X of the middle of the first row
     * @param startZ the Z of the middle of the first row
     * @param stepX  the X step from a row to the next one, to the inside of the village
     * @param stepZ  the Z step from a row to the next one, to the inside of the village
     * @param length the number of rows, 1 at least
     * @param end    the level of the piece at the end of the arm, or empty if the arm stops at the outline
     */
    record Arm(int startX, int startZ, int stepX, int stepZ, int length, OptionalInt end) {
        int x(int row) {
            return startX + row * stepX;
        }

        int z(int row) {
            return startZ + row * stepZ;
        }

        /**
         * The row of the arm across a position: the row with the same distance from the gate. Empty if the position is
         * not in the width of a street ({@code halfWidth} blocks on each side of the middle) or not along the arm.
         */
        OptionalInt row(int x, int z, int halfWidth) {
            int along = (x - startX) * stepX + (z - startZ) * stepZ;
            int across = (x - startX) * stepZ + (z - startZ) * stepX;
            if (along < 0 || along >= length || Math.abs(across) > halfWidth) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(along);
        }
    }

    /** The pieces of the pools that stop an arm: all the pieces that are not streets. */
    private static List<Stop> stops(List<StructurePiece> village) {
        List<Stop> stops = new ArrayList<>();
        for (StructurePiece piece : village) {
            if (!isStreet(piece)) {
                BoundingBox box = piece.getBoundingBox();
                stops.add(new Stop(box(box), box.minY()));
            }
        }
        return stops;
    }

    /** True for a street: a piece of the pools that follows the terrain. */
    static boolean isStreet(StructurePiece piece) {
        return piece instanceof PoolElementStructurePiece pool
                && pool.getElement().getProjection() == StructureTemplatePool.Projection.TERRAIN_MATCHING;
    }

    /**
     * The arm of a gate. Empty if the gate is next to a piece that is not a street.
     *
     * @param layout the layout of the wall
     * @param gate   the gate
     * @param stops  the pieces of the pools that are not streets
     */
    static Optional<Arm> arm(WallLayout layout, Gate gate, List<Stop> stops) {
        Side side = gate.side();
        Cell middle = layout.cell(side, layout.along(gate));
        int startX = middle.x() + 2 * side.insideX();
        int startZ = middle.z() + 2 * side.insideZ();
        Box outline = layout.outline();
        int length = 0;
        OptionalInt end = OptionalInt.empty();
        while (true) {
            int x = startX + length * side.insideX();
            int z = startZ + length * side.insideZ();
            if (!outline.contains(x, z)) {
                break;
            }
            Optional<Stop> stop = stops.stream().filter(candidate -> candidate.box().contains(x, z)).findFirst();
            if (stop.isPresent()) {
                end = OptionalInt.of(stop.get().level());
                break;
            }
            length++;
        }
        if (length == 0) {
            return Optional.empty();
        }
        return Optional.of(new Arm(startX, startZ, side.insideX(), side.insideZ(), length, end));
    }

    /**
     * The levels of the rows of an arm: a ramp from the level of the gate to the level of the piece at the end of the
     * arm (see {@link PathRamp}). Between them, the ramp stays near the terrain.
     */
    private static int[] levels(Arm arm, int gateLevel, Terrain terrain) {
        int[] heights = new int[arm.length()];
        for (int row = 0; row < heights.length; row++) {
            heights[row] = terrain.height(arm.x(row), arm.z(row));
        }
        arm.end().ifPresent(end -> heights[heights.length - 1] = end);
        return PathRamp.levels(gateLevel, heights);
    }

    /**
     * Moves the houses on the plots of an arm up or down: the connector of a house goes 1 block above the level of the
     * arm at its plot. Thus the door of the house stays at the level of the street.
     */
    private static void moveHouses(StructureTemplateManager templates, List<StructurePiece> village, Arm arm,
            int[] levels) {
        for (Plot plot : VillagePlots.of(templates, village)) {
            if (!plot.built()) {
                continue;
            }
            BlockPos connector = plot.connector();
            OptionalInt row = arm.row(connector.getX(), connector.getZ(), STREET_HALF_WIDTH);
            if (row.isEmpty()) {
                continue;
            }
            BlockPos front = plot.front();
            for (StructurePiece piece : village) {
                if (piece instanceof PoolElementStructurePiece house && !isStreet(house)
                        && house.getBoundingBox().isInside(front.getX(), house.getBoundingBox().minY(), front.getZ())) {
                    OptionalInt y = VillagePlots.connectorY(templates, house, plot.target(), front.getX(),
                            front.getZ());
                    if (y.isPresent()) {
                        house.move(0, levels[row.getAsInt()] + 1 - y.getAsInt(), 0);
                    }
                }
            }
        }
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
