package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.village.PalisadeHeights;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.structure.FortifiedVillageStructure;
import io.github.nicolasgoyon.folkhold.world.village.structure.PalisadePiece;
import io.github.nicolasgoyon.folkhold.world.village.structure.PierPiece;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * The reference state of a village for the repairs (see {@link VillageRepairs}) and for its growth (see
 * {@link VillageGrowth}): the structure pieces of the village at the generation.
 *
 * <ul>
 *   <li>A template piece is a structure template at a position, with a turn. The template pieces come in the order of
 *       the generation: a piece replaces the blocks of the pieces before it. A piece that follows the terrain (the
 *       streets) is not a part of the reference: its blocks go to the height of the terrain.</li>
 *   <li>A palisade is a segment of the palisade: the base block and the logs of each column (see
 *       {@link PalisadePiece}). The point on top of a column and the foundation under it are not a part of the
 *       reference. The logs above the first {@link PalisadeHeights#LOGS} logs are not a part of it too.</li>
 *   <li>The area is the box of the village inside its palisade, the palisade included, from the lowest to the highest
 *       template piece. A village with no palisade pieces has no area.</li>
 *   <li>The plots are the places for a house along the streets, with and without a house (see
 *       {@link VillagePlots}).</li>
 *   <li>The homes are the pool of the homes that the builders build on the free plots (see {@link VillageGrowth}).</li>
 *   <li>The piers are the boxes of the piers at the water gates (see {@link PierPiece}): the deck, the rails and the
 *       posts down to the floor of the water.</li>
 * </ul>
 *
 * @param pieces    the template pieces of the village
 * @param palisades the segments of the palisade
 * @param area      the box of the village inside its palisade, or empty
 * @param plots     the plots of the streets
 * @param homes     the id of the pool of the homes, or empty
 * @param piers     the boxes of the piers
 */
public record VillageReference(List<Piece> pieces, List<Palisade> palisades, Optional<BoundingBox> area,
        List<VillagePlots.Plot> plots, Optional<ResourceLocation> homes, List<BoundingBox> piers) {
    /**
     * A structure piece of a village.
     *
     * @param template the id of the structure template
     * @param origin   the position of the origin of the template in the level
     * @param rotation the turn of the template
     */
    public record Piece(ResourceLocation template, BlockPos origin, Rotation rotation) {
        public Piece {
            origin = origin.immutable();
        }
    }

    /**
     * A segment of the palisade of a village: a straight line of columns.
     *
     * @param start the base of the first column
     * @param stepX the X step from a column to the next one
     * @param stepZ the Z step from a column to the next one
     * @param bases the Y of the base of each column
     * @param base  the base block
     * @param log   the log block
     */
    public record Palisade(BlockPos start, int stepX, int stepZ, List<Integer> bases, BlockState base,
            BlockState log) {
        public Palisade {
            start = start.immutable();
            bases = List.copyOf(bases);
        }

        /** The palisade of a structure piece. */
        public static Palisade of(PalisadePiece piece) {
            int[] bases = piece.bases();
            List<Integer> list = new ArrayList<>(bases.length);
            for (int base : bases) {
                list.add(base);
            }
            return new Palisade(new BlockPos(piece.startX(), bases[0], piece.startZ()), piece.stepX(), piece.stepZ(),
                    list, piece.blocks().base(), piece.blocks().log());
        }

        /** The blocks of the palisade: the base block and the logs of each column. */
        public Map<BlockPos, BlockState> blocks() {
            Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
            for (int i = 0; i < bases.size(); i++) {
                int x = start.getX() + stepX * i;
                int z = start.getZ() + stepZ * i;
                int y = bases.get(i);
                blocks.put(new BlockPos(x, y, z), base);
                for (int log = 1; log <= PalisadeHeights.LOGS; log++) {
                    blocks.put(new BlockPos(x, y + log, z), this.log);
                }
            }
            return blocks;
        }
    }

    public VillageReference {
        pieces = List.copyOf(pieces);
        palisades = List.copyOf(palisades);
        plots = List.copyOf(plots);
        piers = List.copyOf(piers);
    }

    /** A reference with no piers. */
    public VillageReference(List<Piece> pieces, List<Palisade> palisades, Optional<BoundingBox> area,
            List<VillagePlots.Plot> plots, Optional<ResourceLocation> homes) {
        this(pieces, palisades, area, plots, homes, List.of());
    }

    /** A reference with no plots, no pool of homes and no piers. */
    public VillageReference(List<Piece> pieces, List<Palisade> palisades, Optional<BoundingBox> area) {
        this(pieces, palisades, area, List.of(), Optional.empty());
    }

    /** A reference with template pieces only: no palisade, no area, no plots and no pool of homes. */
    public VillageReference(List<Piece> pieces) {
        this(pieces, List.of(), Optional.empty());
    }

    /** This reference with a pool of homes. */
    public VillageReference withHomes(ResourceLocation pool) {
        return new VillageReference(pieces, palisades, area, plots, Optional.of(pool), piers);
    }

    /** The blocks of all the palisades, in the order of the palisades. */
    public Map<BlockPos, BlockState> palisadeBlocks() {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (Palisade palisade : palisades) {
            blocks.putAll(palisade.blocks());
        }
        return blocks;
    }

    /**
     * True if a position is in the area of the village, on the horizontal plane.
     *
     * @param pos the position
     * @return true for a position in the area, or false for a village with no area
     */
    public boolean isInArea(BlockPos pos) {
        return area.map(box -> pos.getX() >= box.minX() && pos.getX() <= box.maxX() && pos.getZ() >= box.minZ()
                && pos.getZ() <= box.maxZ()).orElse(false);
    }

    /**
     * The reference of the village at a position: the pieces of the Folkhold village structure that contains the
     * position. A piece that is not a structure template on the terrain or a palisade does not count.
     *
     * @param level the level
     * @param heart the position of the village heart
     * @return the reference, or empty if no village structure contains the position
     */
    public static Optional<VillageReference> find(ServerLevel level, BlockPos heart) {
        Optional<Structure> structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getOptional(Settlements.VILLAGE_STRUCTURE);
        if (structure.isEmpty()) {
            return Optional.empty();
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(heart, structure.get());
        if (!start.isValid()) {
            return Optional.empty();
        }
        Optional<ResourceLocation> homes = start.getStructure() instanceof FortifiedVillageStructure village
                ? village.variantOf(level, start.getPieces()).homes().unwrapKey().map(ResourceKey::location)
                : Optional.empty();
        return of(level, start.getPieces()).map(reference -> homes.map(reference::withHomes).orElse(reference));
    }

    /**
     * The reference of the pieces of a village structure.
     *
     * @param level  the level
     * @param pieces the pieces of the structure
     * @return the reference, or empty if no piece counts
     */
    public static Optional<VillageReference> of(ServerLevel level, List<StructurePiece> pieces) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        List<Piece> templates = new ArrayList<>();
        List<Palisade> palisades = new ArrayList<>();
        List<BoundingBox> templateBoxes = new ArrayList<>();
        List<BoundingBox> palisadeBoxes = new ArrayList<>();
        List<BoundingBox> piers = new ArrayList<>();
        for (StructurePiece piece : pieces) {
            if (piece instanceof PoolElementStructurePiece pool
                    && pool.getElement().getProjection() == StructureTemplatePool.Projection.RIGID) {
                Optional<ResourceLocation> template = templateOf(ops, pool.getElement());
                if (template.isPresent()) {
                    templates.add(new Piece(template.get(), pool.getPosition(), pool.getRotation()));
                    templateBoxes.add(pool.getBoundingBox());
                }
            } else if (piece instanceof PalisadePiece palisade) {
                palisades.add(Palisade.of(palisade));
                palisadeBoxes.add(palisade.getBoundingBox());
            } else if (piece instanceof PierPiece pier) {
                piers.add(pier.getBoundingBox());
            }
        }
        if (templates.isEmpty()) {
            return Optional.empty();
        }
        BoundingBox templateBox = BoundingBox.encapsulatingBoxes(templateBoxes).orElseThrow();
        Optional<BoundingBox> area = BoundingBox.encapsulatingBoxes(palisadeBoxes)
                .map(box -> new BoundingBox(box.minX(), templateBox.minY(), box.minZ(), box.maxX(),
                        templateBox.maxY(), box.maxZ()));
        return Optional.of(new VillageReference(templates, palisades, area,
                VillagePlots.of(level.getStructureManager(), pieces), Optional.empty(), piers));
    }

    /**
     * The id of the template of a pool element.
     *
     * @param level   the level
     * @param element the pool element
     * @return the id, or empty for an element with no single template
     */
    public static Optional<ResourceLocation> templateOf(ServerLevel level, StructurePoolElement element) {
        return templateOf(RegistryOps.create(NbtOps.INSTANCE, level.registryAccess()), element);
    }

    /** The id of the template of a pool element, from the saved form of the element. */
    private static Optional<ResourceLocation> templateOf(RegistryOps<Tag> ops, StructurePoolElement element) {
        return StructurePoolElement.CODEC.encodeStart(ops, element)
                .resultOrPartial(error -> FolkholdWorld.LOGGER.debug("Repair: pool element not saved: {}", error))
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .filter(tag -> tag.contains("location", Tag.TAG_STRING))
                .map(tag -> ResourceLocation.tryParse(tag.getString("location")));
    }

    /** The reference as NBT data. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag pieceList = new ListTag();
        for (Piece piece : pieces) {
            CompoundTag pieceTag = new CompoundTag();
            pieceTag.putString("Template", piece.template().toString());
            pieceTag.putLong("Origin", piece.origin().asLong());
            pieceTag.putString("Rotation", piece.rotation().name());
            pieceList.add(pieceTag);
        }
        tag.put("Pieces", pieceList);
        ListTag palisadeList = new ListTag();
        for (Palisade palisade : palisades) {
            CompoundTag palisadeTag = new CompoundTag();
            palisadeTag.putLong("Start", palisade.start().asLong());
            palisadeTag.putInt("StepX", palisade.stepX());
            palisadeTag.putInt("StepZ", palisade.stepZ());
            palisadeTag.putIntArray("Bases", palisade.bases());
            palisadeTag.put("Base", NbtUtils.writeBlockState(palisade.base()));
            palisadeTag.put("Log", NbtUtils.writeBlockState(palisade.log()));
            palisadeList.add(palisadeTag);
        }
        tag.put("Palisades", palisadeList);
        area.ifPresent(box -> tag.putIntArray("Area", boxArray(box)));
        ListTag plotList = new ListTag();
        for (VillagePlots.Plot plot : plots) {
            CompoundTag plotTag = new CompoundTag();
            plotTag.putLong("Connector", plot.connector().asLong());
            plotTag.putString("Facing", plot.facing().getSerializedName());
            plotTag.putString("Target", plot.target().toString());
            plotTag.putBoolean("Built", plot.built());
            plotList.add(plotTag);
        }
        tag.put("Plots", plotList);
        homes.ifPresent(pool -> tag.putString("Homes", pool.toString()));
        ListTag pierList = new ListTag();
        for (BoundingBox pier : piers) {
            pierList.add(new IntArrayTag(boxArray(pier)));
        }
        tag.put("Piers", pierList);
        return tag;
    }

    private static int[] boxArray(BoundingBox box) {
        return new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
    }

    private static Optional<BoundingBox> box(int[] values) {
        return values.length == 6
                ? Optional.of(new BoundingBox(values[0], values[1], values[2], values[3], values[4], values[5]))
                : Optional.empty();
    }

    /**
     * The reference from NBT data (see {@link #save()}).
     *
     * @param blocks the blocks of the game
     * @param tag    the data
     * @return the reference, or empty for data with no template piece
     */
    public static Optional<VillageReference> load(HolderGetter<Block> blocks, CompoundTag tag) {
        List<Piece> pieces = new ArrayList<>();
        ListTag pieceList = tag.getList("Pieces", Tag.TAG_COMPOUND);
        for (int i = 0; i < pieceList.size(); i++) {
            CompoundTag pieceTag = pieceList.getCompound(i);
            ResourceLocation template = ResourceLocation.tryParse(pieceTag.getString("Template"));
            Optional<Rotation> rotation = rotation(pieceTag.getString("Rotation"));
            if (template != null && rotation.isPresent()) {
                pieces.add(new Piece(template, BlockPos.of(pieceTag.getLong("Origin")), rotation.get()));
            }
        }
        List<Palisade> palisades = new ArrayList<>();
        ListTag palisadeList = tag.getList("Palisades", Tag.TAG_COMPOUND);
        for (int i = 0; i < palisadeList.size(); i++) {
            CompoundTag palisadeTag = palisadeList.getCompound(i);
            int[] bases = palisadeTag.getIntArray("Bases");
            if (bases.length > 0) {
                List<Integer> list = new ArrayList<>(bases.length);
                for (int base : bases) {
                    list.add(base);
                }
                palisades.add(new Palisade(BlockPos.of(palisadeTag.getLong("Start")), palisadeTag.getInt("StepX"),
                        palisadeTag.getInt("StepZ"), list,
                        NbtUtils.readBlockState(blocks, palisadeTag.getCompound("Base")),
                        NbtUtils.readBlockState(blocks, palisadeTag.getCompound("Log"))));
            }
        }
        Optional<BoundingBox> area = box(tag.getIntArray("Area"));
        List<VillagePlots.Plot> plots = new ArrayList<>();
        ListTag plotList = tag.getList("Plots", Tag.TAG_COMPOUND);
        for (int i = 0; i < plotList.size(); i++) {
            CompoundTag plotTag = plotList.getCompound(i);
            Direction facing = Direction.byName(plotTag.getString("Facing"));
            ResourceLocation target = ResourceLocation.tryParse(plotTag.getString("Target"));
            if (facing != null && target != null) {
                plots.add(new VillagePlots.Plot(BlockPos.of(plotTag.getLong("Connector")), facing, target,
                        plotTag.getBoolean("Built")));
            }
        }
        Optional<ResourceLocation> homes = tag.contains("Homes", Tag.TAG_STRING)
                ? Optional.ofNullable(ResourceLocation.tryParse(tag.getString("Homes"))) : Optional.empty();
        List<BoundingBox> piers = new ArrayList<>();
        ListTag pierList = tag.getList("Piers", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < pierList.size(); i++) {
            box(pierList.getIntArray(i)).ifPresent(piers::add);
        }
        return pieces.isEmpty() ? Optional.empty()
                : Optional.of(new VillageReference(pieces, palisades, area, plots, homes, piers));
    }

    private static Optional<Rotation> rotation(String name) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.name().equals(name)) {
                return Optional.of(rotation);
            }
        }
        return Optional.empty();
    }
}
