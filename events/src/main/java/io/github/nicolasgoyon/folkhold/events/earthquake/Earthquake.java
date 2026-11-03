package io.github.nicolasgoyon.folkhold.events.earthquake;

import io.github.nicolasgoyon.folkhold.events.block.BlockClass;
import io.github.nicolasgoyon.folkhold.events.block.EventBlockTags;
import io.github.nicolasgoyon.folkhold.events.common.BlockChanges;
import io.github.nicolasgoyon.folkhold.events.common.BlockRules;
import io.github.nicolasgoyon.folkhold.events.common.EventMath;
import io.github.nicolasgoyon.folkhold.events.common.WorldEvent;
import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;
import io.github.nicolasgoyon.folkhold.events.network.EarthquakeShakePayload;
import io.github.nicolasgoyon.folkhold.events.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.events.registry.ModSounds;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One earthquake in a level. {@link Earthquakes} starts it and calls {@link #tick()} each tick.
 *
 * <ol>
 *   <li>Announce: the players near the area hear a rumble, get a message and a light camera shake. The animals of the
 *       area run away from the epicenter.</li>
 *   <li>Shake: a stronger camera shake. The earthquake examines the columns of its area in a random order, spread
 *       over the time of the shake. In each column, it examines the blocks from the top block down to
 *       {@link EventsConfig#EARTHQUAKE_SCAN_DEPTH} blocks. An exposed block (a block with a free neighbor) takes
 *       damage with a chance from its class (see {@link BlockClass}), the intensity at its distance from the epicenter
 *       and the strength of the earthquake:
 *       <ul>
 *         <li>a block of the tag {@code folkhold_events:shatters} breaks with no drop</li>
 *         <li>a block with a free position below it falls</li>
 *         <li>else, a block with a free position on one side falls to this side</li>
 *         <li>else, a block with a cracked variant (stone bricks, deepslate bricks) becomes this variant</li>
 *       </ul>
 *       The roof of a cavity below the surface can collapse into falling blocks. Some cracks open in the natural
 *       ground (tags {@code folkhold_events:crack_surface} and {@code folkhold_events:crackable}). The falling blocks hurt the entities below them.</li>
 * </ol>
 *
 * <p>The earthquake never changes a protected block (see {@link EventBlockTags#isProtected}), the two parts of a door
 * or a tall plant, the attached blocks (torches, flowers), a fluid, and the natural trees (natural leaves and the
 * logs below them). It does not move the support of a protected block, of a block with a block entity or of an
 * attached block, and it does not move a block with an item frame or a painting on it. It examines {@value #MAX_COLUMNS_PER_TICK} columns at most in one tick and changes
 * {@link EarthquakeSettings#blocksPerTick()} blocks at most in one tick. The other columns and changes wait for the
 * next ticks. It does not load chunks: it ignores the columns of chunks that do not tick.
 */
public final class Earthquake implements WorldEvent {
    /** Strength of the camera shake at the epicenter for a strength of 1, in degrees. */
    private static final float SHAKE_DEGREES = 1.5F;
    /** The largest camera shake, in degrees. */
    private static final float MAX_SHAKE_DEGREES = 5.0F;
    /** Part of the camera shake during the announce. */
    private static final float ANNOUNCE_SHAKE = 0.3F;
    /** The players at this distance from the area or nearer hear the earthquake, in blocks. */
    private static final int HEARING_MARGIN = 48;
    /** The animals at this distance from the area or nearer run away, in blocks. */
    private static final int FLEE_MARGIN = 16;
    /** Speed of the animals that run away, relative to their walk. */
    private static final double FLEE_SPEED = 1.5;
    /** Interval of the rumble and of the orders to run away, in ticks. */
    private static final int RUMBLE_INTERVAL = 20;
    /** The scan of the columns stops while this number of batches waits. */
    private static final int MAX_WAITING_BATCHES = 4;
    /** The largest number of columns that the earthquake examines in one tick. */
    private static final int MAX_COLUMNS_PER_TICK = 64;
    /** Blocks that become a cracked variant. */
    private static final Map<Block, Block> CRACKED = Map.of(
            Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
            Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS,
            Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES,
            Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS,
            Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);

    /** The phase of an earthquake. */
    public enum Phase {
        ANNOUNCE,
        SHAKE,
        DONE
    }

    /** The change of one block. */
    private enum Action {
        /** The block becomes a falling block, below it or to one side. */
        FALL,
        /** The block breaks with no drop. */
        SHATTER,
        /** The block becomes its cracked variant. */
        CRACK,
        /** The block goes away with its drops: a crack in the surface. */
        REMOVE
    }

    /**
     * A change that waits for its batch. The change applies only if the block is still the expected block.
     *
     * @param side   for a fall: {@link Direction#DOWN}, or the horizontal side of the fall
     * @param result for a crack: the cracked block
     */
    private record Change(BlockPos pos, BlockState expected, Action action, Direction side, BlockState result)
            implements BlockChanges.Change {
    }

    private final ServerLevel level;
    private final BlockPos center;
    private final EarthquakeSettings settings;
    private final RandomSource random;
    private final BlockChanges<Change> pending = new BlockChanges<>();
    private Phase phase = Phase.ANNOUNCE;
    private int elapsed;
    private long[] columns = new long[0];
    private int nextColumn;
    private int columnsPerTick = 1;

    /** The columns of the cracks in the surface, and the depth of the crack in each column. */
    private long[] crackColumns = new long[0];
    private int[] crackDepths = new int[0];
    private int nextCrack;

    private int changedBlocks;

    Earthquake(ServerLevel level, BlockPos center, EarthquakeSettings settings, long seed) {
        this.level = level;
        this.center = center.immutable();
        this.settings = settings;
        this.random = RandomSource.create(seed);
    }

    @Override
    public BlockPos center() {
        return center;
    }

    @Override
    public int radius() {
        return settings.radius();
    }

    public EarthquakeSettings settings() {
        return settings;
    }

    public Phase phase() {
        return phase;
    }

    @Override
    public boolean isDone() {
        return phase == Phase.DONE;
    }

    /** The number of blocks that the earthquake changed. */
    public int changedBlocks() {
        return changedBlocks;
    }

    /** The number of ticks from the start. */
    public int elapsedTicks() {
        return elapsed;
    }

    /** Stops the earthquake. The changes that wait do not apply. */
    @Override
    public void stop() {
        phase = Phase.DONE;
        pending.clear();
    }

    /** Runs one tick of the earthquake. */
    @Override
    public void tick() {
        if (phase == Phase.DONE) {
            return;
        }
        elapsed++;
        if (elapsed == 1) {
            announce();
        }
        if (phase == Phase.ANNOUNCE && elapsed > settings.announceTicks()) {
            startShake();
        }
        if (elapsed % RUMBLE_INTERVAL == 1) {
            rumble();
            fleeAnimals();
        }
        if (phase == Phase.SHAKE) {
            scanColumns();
            changedBlocks += pending.apply(level, settings.blocksPerTick(), this::apply);
            if (nextColumn >= columns.length && nextCrack >= crackColumns.length && pending.isEmpty()
                    && elapsed > settings.announceTicks() + settings.durationTicks()) {
                phase = Phase.DONE;
            }
        }
    }

    private void announce() {
        for (ServerPlayer player : nearPlayers()) {
            player.displayClientMessage(Component.translatable("folkhold_events.earthquake.announce"), true);
        }
        if (settings.announceTicks() > 0) {
            sendShake(ANNOUNCE_SHAKE, settings.announceTicks());
        }
    }

    private void startShake() {
        phase = Phase.SHAKE;
        int radius = settings.radius();
        List<Long> found = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    found.add(BlockPos.asLong(center.getX() + dx, 0, center.getZ() + dz));
                }
            }
        }
        columns = found.stream().mapToLong(Long::longValue).toArray();
        for (int i = columns.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            long swap = columns[i];
            columns[i] = columns[j];
            columns[j] = swap;
        }
        columnsPerTick = Mth.clamp(Mth.ceil(columns.length / (double) settings.durationTicks()), 1,
                MAX_COLUMNS_PER_TICK);
        planCracks();
        sendShake(1.0F, settings.durationTicks());
    }

    /** The players that hear the earthquake. */
    private List<ServerPlayer> nearPlayers() {
        double range = settings.radius() + HEARING_MARGIN;
        return level.players().stream()
                .filter(player -> horizontalDistanceSquared(player.blockPosition()) <= range * range)
                .toList();
    }

    /** A value from 1 at the epicenter to 0 at the hearing distance. */
    private float closeness(ServerPlayer player) {
        double range = settings.radius() + HEARING_MARGIN;
        return (float) Math.max(0.0, 1.0 - Math.sqrt(horizontalDistanceSquared(player.blockPosition())) / range);
    }

    private void sendShake(float part, int ticks) {
        for (ServerPlayer player : nearPlayers()) {
            float amplitude = Math.min(MAX_SHAKE_DEGREES,
                    SHAKE_DEGREES * part * (float) settings.strength() * closeness(player));
            if (amplitude > 0.0F) {
                ModNetwork.send(player, new EarthquakeShakePayload(amplitude, ticks));
            }
        }
    }

    private void rumble() {
        for (ServerPlayer player : nearPlayers()) {
            float volume = 0.4F + 1.2F * closeness(player);
            player.playNotifySound(ModSounds.EARTHQUAKE_RUMBLE.get(), SoundSource.AMBIENT, volume,
                    0.8F + 0.2F * random.nextFloat());
        }
    }

    /** The animals of the area walk fast to a position away from the epicenter. */
    private void fleeAnimals() {
        Vec3 epicenter = Vec3.atBottomCenterOf(center);
        AABB area = new AABB(center).inflate(settings.radius() + FLEE_MARGIN);
        for (Animal animal : level.getEntitiesOfClass(Animal.class, area, Earthquake::canFlee)) {
            if (!level.isPositionEntityTicking(animal.blockPosition())) {
                continue;
            }
            Vec3 target = DefaultRandomPos.getPosAway(animal, 16, 7, epicenter);
            if (target != null && BlockRules.isReady(level, BlockPos.containing(target))) {
                animal.getNavigation().moveTo(target.x, target.y, target.z, FLEE_SPEED);
            }
        }
    }

    /**
     * True if an animal runs away: an animal with its AI, not leashed, not a passenger, and not a tamed animal that
     * sits on the order of its owner.
     */
    private static boolean canFlee(Animal animal) {
        return animal.isAlive() && !animal.isNoAi() && !animal.isLeashed() && !animal.isPassenger()
                && !(animal instanceof TamableAnimal tamed && tamed.isOrderedToSit());
    }

    /**
     * Examines the columns of the cracks first, then the other columns. The two share the budget of columns of the
     * tick. The examination stops while too many changes wait.
     */
    private void scanColumns() {
        int budget = columnsPerTick;
        while (budget > 0 && pending.size() < settings.blocksPerTick() * MAX_WAITING_BATCHES) {
            if (nextCrack < crackColumns.length) {
                long column = crackColumns[nextCrack];
                openCrack(BlockPos.getX(column), BlockPos.getZ(column), crackDepths[nextCrack]);
                nextCrack++;
            } else if (nextColumn < columns.length) {
                long column = columns[nextColumn++];
                scanColumn(BlockPos.getX(column), BlockPos.getZ(column));
            } else {
                return;
            }
            budget--;
        }
    }

    private void scanColumn(int x, int z) {
        if (!BlockRules.isReady(level, new BlockPos(x, center.getY(), z))) {
            return;
        }
        double intensity = intensity(x, z);
        if (intensity <= 0.0) {
            return;
        }
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int bottom = Math.max(level.getMinBuildHeight(), top - EventsConfig.EARTHQUAKE_SCAN_DEPTH.get() + 1);
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            // The roll comes first: most blocks take no damage, and the check of a block reads its neighbors.
            double chance = EventMath.chance(classChance(BlockClass.of(state)), intensity, settings.strength());
            if (random.nextDouble() < chance && isMovable(pos, state)) {
                damage(pos, state).ifPresent(pending::add);
            }
        }
        collapse(x, z, intensity);
    }

    /** The change of a block that takes damage, or empty for a block that is not exposed. */
    private Optional<Change> damage(BlockPos pos, BlockState state) {
        if (state.is(EventBlockTags.SHATTERS)) {
            for (Direction direction : Direction.values()) {
                if (BlockRules.isFree(level, pos.relative(direction))) {
                    return Optional.of(new Change(pos, state, Action.SHATTER, direction, state));
                }
            }
            return Optional.empty();
        }
        if (BlockRules.isFree(level, pos.below())) {
            return Optional.of(new Change(pos, state, Action.FALL, Direction.DOWN, state));
        }
        List<Direction> sides = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (BlockRules.isFree(level, pos.relative(direction))) {
                sides.add(direction);
            }
        }
        if (!sides.isEmpty()) {
            return Optional.of(new Change(pos, state, Action.FALL, sides.get(random.nextInt(sides.size())), state));
        }
        Block cracked = CRACKED.get(state.getBlock());
        if (cracked != null && BlockRules.isFree(level, pos.above())) {
            return Optional.of(new Change(pos, state, Action.CRACK, Direction.UP, cracked.withPropertiesOf(state)));
        }
        return Optional.empty();
    }

    /**
     * The roof of a cavity below the surface of a column collapses: its blocks fall into the cavity. The roof is 2 to
     * {@link EventsConfig#EARTHQUAKE_COLLAPSE_DEPTH} blocks thick, and the cavity is 2 blocks high or more. A roof of
     * one block (the roof of a house) does not collapse in this way: it takes damage as an exposed block.
     */
    private void collapse(int x, int z, double intensity) {
        double factor = EventsConfig.EARTHQUAKE_COLLAPSE_FACTOR.get();
        if (factor <= 0.0) {
            return;
        }
        int maxDepth = EventsConfig.EARTHQUAKE_COLLAPSE_DEPTH.get();
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int y = surface;
        int thickness = 0;
        while (thickness <= maxDepth && y > level.getMinBuildHeight()
                && !FallingBlock.isFree(level.getBlockState(new BlockPos(x, y, z)))) {
            thickness++;
            y--;
        }
        if (thickness < 2 || thickness > maxDepth || !BlockRules.isFree(level, new BlockPos(x, y - 1, z))) {
            return;
        }
        BlockPos roofBottom = new BlockPos(x, y + 1, z);
        double chance = factor * EventMath.chance(classChance(BlockClass.of(level.getBlockState(roofBottom))),
                intensity, settings.strength());
        if (random.nextDouble() >= chance) {
            return;
        }
        List<Change> roof = new ArrayList<>();
        for (int roofY = y + 1; roofY <= surface; roofY++) {
            BlockPos pos = new BlockPos(x, roofY, z);
            BlockState state = level.getBlockState(pos);
            if (!isMovable(pos, state)) {
                return;
            }
            roof.add(new Change(pos, state, Action.FALL, Direction.DOWN, state));
        }
        pending.addAll(roof);
    }

    /**
     * Plans the cracks in the surface: straight lines through random points near the epicenter. The method reads no
     * block. {@link #scanColumns()} opens the cracks, column by column.
     */
    private void planCracks() {
        int count = (int) Math.round(EventsConfig.EARTHQUAKE_CRACKS.get() * settings.strength());
        int maxDepth = EventsConfig.EARTHQUAKE_CRACK_DEPTH.get();
        int radius = settings.radius();
        LongSet done = new LongOpenHashSet();
        List<Long> found = new ArrayList<>();
        List<Integer> depths = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double originX = center.getX() + (random.nextDouble() * 2.0 - 1.0) * radius / 3.0;
            double originZ = center.getZ() + (random.nextDouble() * 2.0 - 1.0) * radius / 3.0;
            double half = radius * (0.4 + 0.4 * random.nextDouble());
            for (double offset = -half; offset <= half; offset += 1.0) {
                int x = Mth.floor(originX + Math.cos(angle) * offset);
                int z = Mth.floor(originZ + Math.sin(angle) * offset);
                long column = BlockPos.asLong(x, 0, z);
                if (intensity(x, z) > 0.0 && done.add(column)) {
                    found.add(column);
                    depths.add(EarthquakeMath.crackDepth(offset, half, maxDepth));
                }
            }
        }
        crackColumns = found.stream().mapToLong(Long::longValue).toArray();
        crackDepths = depths.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Opens a crack in one column: the top blocks of the ground go away, with their drops. The crack opens only in
     * the natural ground:
     * <ul>
     *   <li>The top block of the column is in the tag {@code folkhold_events:crack_surface}: grass, dirt, sand,
     *       gravel. A layer of snow on it does not count.</li>
     *   <li>The top of each horizontal neighbor column is 1 block higher or lower at most. A wall, a pillar or the edge
     *       of a platform stops the crack.</li>
     *   <li>The top block is on the ground: no free position below it.</li>
     * </ul>
     * Below the surface block, the crack removes the blocks of the tag {@code folkhold_events:crackable} that the
     * earthquake can move. It stops at the first other block.
     */
    private void openCrack(int x, int z, int depth) {
        if (!BlockRules.isReady(level, new BlockPos(x, center.getY(), z))) {
            return;
        }
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = new BlockPos(x + direction.getStepX(), center.getY(), z + direction.getStepZ());
            if (!level.isLoaded(neighbor) || Math.abs(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    neighbor.getX(), neighbor.getZ()) - height) > 1) {
                return;
            }
        }
        int top = height - 1;
        if (level.getBlockState(new BlockPos(x, top, z)).is(Blocks.SNOW)) {
            top--;
        }
        if (!level.getBlockState(new BlockPos(x, top, z)).is(EventBlockTags.CRACK_SURFACE)
                || BlockRules.isFree(level, new BlockPos(x, top - 1, z))) {
            return;
        }
        for (int k = 0; k < depth; k++) {
            BlockPos pos = new BlockPos(x, top - k, z);
            BlockState state = level.getBlockState(pos);
            if (!state.is(EventBlockTags.CRACKABLE) || !isMovable(pos, state)) {
                return;
            }
            pending.add(new Change(pos, state, Action.REMOVE, Direction.UP, state));
        }
    }

    private boolean apply(Change change) {
        BlockPos pos = change.pos();
        BlockState state = change.expected();
        return switch (change.action()) {
            case FALL -> fall(pos, state, change.side());
            case SHATTER -> level.destroyBlock(pos, false);
            // A crack gives back its blocks: an error of the tags does not destroy the blocks of a player.
            case REMOVE -> level.destroyBlock(pos, true);
            case CRACK -> {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
                yield level.setBlock(pos, change.result(), Block.UPDATE_ALL);
            }
        };
    }

    /** A block becomes a falling block, below its position or on one side. The block hurts the entities. */
    private boolean fall(BlockPos pos, BlockState state, Direction side) {
        BlockPos target = pos.relative(side);
        if (!BlockRules.isReady(level, target) || !BlockRules.isFree(level, target)) {
            return false;
        }
        FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
        falling.setHurtsEntities(EventsConfig.EARTHQUAKE_FALL_DAMAGE.get().floatValue(),
                EventsConfig.EARTHQUAKE_MAX_FALL_DAMAGE.get());
        if (side != Direction.DOWN) {
            falling.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        }
        return true;
    }

    /**
     * True if the earthquake can change a block: a block with a collision box, no fluid, not protected, not a part
     * of a double block, not attached to another block, not a part of a natural tree, and not the support of a block
     * that must stay (see {@link BlockRules#holdsABlock}).
     */
    private boolean isMovable(BlockPos pos, BlockState state) {
        if (!BlockRules.isChangeable(level, pos, state)) {
            return false;
        }
        if (state.is(BlockTags.LEAVES)) {
            return !BlockRules.isNaturalLeaves(state);
        }
        return (!state.is(BlockTags.LOGS) || !BlockRules.isTreeTrunk(level, pos))
                && !BlockRules.holdsABlock(level, pos);
    }

    private double intensity(int x, int z) {
        double dx = x - center.getX();
        double dz = z - center.getZ();
        return EventMath.intensity(dx * dx + dz * dz, settings.radius());
    }

    private double horizontalDistanceSquared(BlockPos pos) {
        double dx = pos.getX() - center.getX();
        double dz = pos.getZ() - center.getZ();
        return dx * dx + dz * dz;
    }

    private static double classChance(BlockClass blockClass) {
        return switch (blockClass) {
            case FRAGILE -> EventsConfig.EARTHQUAKE_FRAGILE_CHANCE.get();
            case STURDY -> EventsConfig.EARTHQUAKE_STURDY_CHANCE.get();
            case RESISTANT -> EventsConfig.EARTHQUAKE_RESISTANT_CHANCE.get();
        };
    }
}
