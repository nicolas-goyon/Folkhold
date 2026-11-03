package io.github.nicolasgoyon.folkhold.events.storm;

import io.github.nicolasgoyon.folkhold.events.block.BlockClass;
import io.github.nicolasgoyon.folkhold.events.block.EventBlockTags;
import io.github.nicolasgoyon.folkhold.events.common.BlockChanges;
import io.github.nicolasgoyon.folkhold.events.common.BlockRules;
import io.github.nicolasgoyon.folkhold.events.common.EventMath;
import io.github.nicolasgoyon.folkhold.events.common.WorldEvent;
import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;
import io.github.nicolasgoyon.folkhold.events.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.events.network.WindGustPayload;
import io.github.nicolasgoyon.folkhold.events.registry.ModSounds;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One storm in a level. {@link Storms} starts it and calls {@link #tick()} each tick.
 *
 * <ol>
 *   <li>Announce: the players near the area get a message and hear the wind. With the weather of the config, the rain
 *       starts and the sky becomes dark. The storm examines the columns of its area during the announce.</li>
 *   <li>Wind: thunder weather, lightning strikes in the area, gusts. The wind pushes the mobs and the animals under
 *       the open sky, and the gusts push the players and throw the mobs into the air. The front of the storm crosses
 *       the area along the wind, and the wind tears up the selected blocks when the front reaches them.</li>
 * </ol>
 *
 * <p>Selection of the blocks: the storm examines each column of its area once, from the top block down to
 * {@link EventsConfig#STORM_SCAN_DEPTH} blocks. A block is exposed when a face on the upwind side touches a free
 * position under the open sky. An exposed block is a candidate with a chance from its class (see {@link BlockClass}),
 * the intensity at its distance from the center and the strength. The storm keeps
 * {@link StormSettings#maxChanges()} candidates at most, selected at random among all the candidates of the area. Thus
 * a large forest does not concentrate the damage on the upwind edge, and the number of torn blocks has a limit.
 *
 * <p>A torn block:
 * <ul>
 *   <li>A block of the tag {@code folkhold_events:shatters} breaks. Natural leaves drop their loot. The other blocks
 *       (glass, panes, ice, leaves of a player) drop their own item.</li>
 *   <li>The other blocks fly away with the wind as falling blocks. A flying block hurts the entities that it hits and
 *       lands as a block, or drops its item when it cannot land.</li>
 * </ul>
 * The storm never deletes a block with no drop.
 *
 * <p>The storm never changes a protected block (see {@link EventBlockTags#isProtected}), a fluid, a part of a double
 * block, an attached block or the trunk of a natural tree. It does not move the support of a protected block, of a
 * block with a block entity or of an attached block, and it does not move a block with an item frame or a painting on
 * it. It examines {@value #MAX_COLUMNS_PER_TICK} columns at most in one tick and changes
 * {@link StormSettings#blocksPerTick()} blocks at most in one tick. It does not load chunks: it ignores the columns,
 * the entities and the lightning targets of the chunks that do not tick.
 */
public final class Storm implements WorldEvent {
    /** The players at this distance from the area or nearer hear the storm, in blocks. */
    private static final int HEARING_MARGIN = 64;
    /** Interval of the sound of the wind, in ticks. */
    private static final int WIND_SOUND_INTERVAL = 80;
    /** The largest number of columns that the storm examines in one tick. */
    private static final int MAX_COLUMNS_PER_TICK = 64;
    /** The front crosses the area in this part of the time of the wind. */
    private static final double FRONT_PART = 0.75;
    /** With no announce, the examination of the columns takes this part of the time of the wind. */
    private static final double SCAN_PART = 0.25;
    /** The smallest part of the wind against a face of a block for an exposed face. */
    private static final double MIN_FACING = 0.3;
    /** The weather lasts this number of ticks after the planned end of the storm. */
    private static final int WEATHER_MARGIN = 200;
    /** The storm follows this number of flying blocks at most, for their hits. */
    private static final int MAX_FLYING = 64;
    /** Damage of a landing block for each block of fall after the first. */
    private static final float LANDING_DAMAGE = 2.0F;
    /** Speed of a torn block along the wind, for a strength of 1, in blocks per tick. */
    private static final double LAUNCH_SPEED = 0.35;
    private static final double LAUNCH_SPEED_RANDOM = 0.25;
    private static final double MAX_LAUNCH_SPEED = 1.0;
    /** Upward speed of a torn block, in blocks per tick. */
    private static final double LAUNCH_LIFT = 0.2;
    private static final double LAUNCH_LIFT_RANDOM = 0.2;
    /** The wind pushes the entities at this interval, in ticks. */
    private static final int PUSH_INTERVAL = 2;
    /** The vertical range of the push around the center, in blocks. */
    private static final int ENTITY_BELOW = 32;
    private static final int ENTITY_ABOVE = 96;
    /** Time between two gusts, in ticks. */
    private static final int GUST_MIN_INTERVAL = 60;
    private static final int GUST_MAX_INTERVAL = 160;
    /** Time of a gust, in ticks. */
    private static final int GUST_TICKS = 20;
    /** A gust multiplies the push of the wind by this factor. */
    private static final double GUST_FACTOR = 3.0;
    /** Push of a gust on a player along the wind, for a strength of 1, in blocks per tick. */
    private static final double PLAYER_GUST = 0.25;
    /** Speed of a mob that a gust throws, along the wind and up, in blocks per tick. */
    private static final double THROW_SPEED = 0.5;
    private static final double THROW_LIFT = 0.45;
    /** A lightning rod at this distance from a strike or nearer attracts it, in blocks. */
    private static final int LIGHTNING_ROD_RANGE = 32;

    /** The phase of a storm. */
    public enum Phase {
        ANNOUNCE,
        WIND,
        DONE
    }

    /**
     * A torn block that waits for the front.
     *
     * @param front the position of the block along the wind, from 0 to 1 (see {@link StormMath#front})
     */
    private record Change(BlockPos pos, BlockState expected, double front) implements BlockChanges.Change {
    }

    /** A flying block, and the entities that it hit. */
    private record Flying(FallingBlockEntity entity, IntSet hit) {
    }

    private final ServerLevel level;
    private final BlockPos center;
    private final StormSettings settings;
    private final RandomSource random;
    private final double windX;
    private final double windZ;
    private final List<Direction> upwindFaces = new ArrayList<>(2);
    /** The horizontal direction nearest to the wind. */
    private final Direction downwind;
    private final BlockChanges<Change> pending = new BlockChanges<>();
    private final List<Flying> flying = new ArrayList<>();
    private Phase phase = Phase.ANNOUNCE;
    private int elapsed;

    private long[] columns = new long[0];
    private int nextColumn;
    private int columnsPerTick = 1;
    /** The selected candidates, and the number of candidates that the storm saw. */
    private final List<Change> candidates = new ArrayList<>();
    private int seenCandidates;
    private boolean scanned;
    private Change[] planned = new Change[0];
    private int nextPlanned;
    private int frontStart;

    private int nextGust;
    private int gustEnd;
    private boolean weatherSet;
    private int changedBlocks;
    private int lightningStrikes;

    Storm(ServerLevel level, BlockPos center, StormSettings settings, long seed) {
        this.level = level;
        this.center = center.immutable();
        this.settings = settings;
        this.random = RandomSource.create(seed);
        this.windX = StormMath.windX(settings.windAngle());
        this.windZ = StormMath.windZ(settings.windAngle());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (StormMath.facesWind(direction.getStepX(), direction.getStepZ(), windX, windZ, MIN_FACING)) {
                upwindFaces.add(direction);
            }
        }
        this.downwind = Direction.getNearest(windX, 0.0, windZ);
    }

    @Override
    public BlockPos center() {
        return center;
    }

    @Override
    public int radius() {
        return settings.radius();
    }

    public StormSettings settings() {
        return settings;
    }

    public Phase phase() {
        return phase;
    }

    @Override
    public boolean isDone() {
        return phase == Phase.DONE;
    }

    /** The number of blocks that the storm tore up. */
    public int changedBlocks() {
        return changedBlocks;
    }

    /** The number of blocks that the storm selected. The storm tears up these blocks when its front reaches them. */
    public int plannedBlocks() {
        return scanned ? planned.length : candidates.size();
    }

    /** The number of lightning strikes of the storm. */
    public int lightningStrikes() {
        return lightningStrikes;
    }

    /** The number of ticks from the start. */
    public int elapsedTicks() {
        return elapsed;
    }

    /** Stops the storm. The changes that wait do not apply. The weather of the storm stops. */
    @Override
    public void stop() {
        finish();
        pending.clear();
    }

    /** Runs one tick of the storm. */
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
            startWind();
        }
        if (!scanned) {
            scanColumns();
        }
        if (elapsed % WIND_SOUND_INTERVAL == 1) {
            windSound();
        }
        if (phase == Phase.WIND) {
            releaseChanges();
            changedBlocks += pending.apply(level, settings.blocksPerTick(), this::apply);
            gusts();
            if (elapsed % PUSH_INTERVAL == 0) {
                pushMobs(isGust() ? GUST_FACTOR : 1.0);
            }
            tickFlying();
            lightning();
            if (scanned && nextPlanned >= planned.length && pending.isEmpty()
                    && elapsed > settings.announceTicks() + settings.durationTicks()) {
                finish();
            }
        }
    }

    private void announce() {
        for (ServerPlayer player : nearPlayers()) {
            player.displayClientMessage(Component.translatable("folkhold_events.storm.announce"), true);
        }
        if (settings.weather()) {
            level.setWeatherParameters(0, settings.announceTicks() + settings.durationTicks() + WEATHER_MARGIN, true,
                    false);
            weatherSet = true;
        }
        int radius = settings.radius();
        List<Long> found = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    found.add(BlockPos.asLong(center.getX() + dx, 0, center.getZ() + dz));
                }
            }
        }
        columns = settings.maxChanges() > 0 ? found.stream().mapToLong(Long::longValue).toArray() : new long[0];
        int scanTicks = settings.announceTicks() > 0 ? settings.announceTicks()
                : Math.max(1, (int) (settings.durationTicks() * SCAN_PART));
        columnsPerTick = Mth.clamp(Mth.ceil(columns.length / (double) scanTicks), 1, MAX_COLUMNS_PER_TICK);
    }

    private void startWind() {
        phase = Phase.WIND;
        for (ServerPlayer player : nearPlayers()) {
            player.displayClientMessage(Component.translatable("folkhold_events.storm.start"), true);
        }
        if (settings.weather()) {
            level.setWeatherParameters(0, settings.durationTicks() + WEATHER_MARGIN, true, true);
            weatherSet = true;
        }
        nextGust = elapsed + GUST_MIN_INTERVAL / 2;
    }

    /** Ends the storm. The weather of the storm stops when no other storm of the level keeps it. */
    private void finish() {
        phase = Phase.DONE;
        if (weatherSet) {
            weatherSet = false;
            boolean otherStorm = Storms.active(level).stream()
                    .anyMatch(other -> other != this && other.weatherSet && !other.isDone());
            if (!otherStorm) {
                level.setWeatherParameters(ServerLevel.RAIN_DELAY.sample(random), 0, false, false);
            }
        }
    }

    /** The players that hear the storm. */
    private List<ServerPlayer> nearPlayers() {
        double range = settings.radius() + HEARING_MARGIN;
        return level.players().stream()
                .filter(player -> horizontalDistanceSquared(player.getX(), player.getZ()) <= range * range)
                .toList();
    }

    private void windSound() {
        double range = settings.radius() + HEARING_MARGIN;
        float loudness = phase == Phase.WIND ? 1.0F
                : 0.3F + 0.7F * Math.min(1.0F, elapsed / (float) Math.max(1, settings.announceTicks()));
        for (ServerPlayer player : nearPlayers()) {
            float closeness = (float) Math.max(0.0,
                    1.0 - Math.sqrt(horizontalDistanceSquared(player.getX(), player.getZ())) / range);
            player.playNotifySound(ModSounds.STORM_WIND.get(), SoundSource.WEATHER,
                    loudness * (0.3F + 0.7F * closeness), 0.8F + 0.3F * random.nextFloat());
        }
    }

    /** Examines the columns of the tick. At the end of the examination, the storm plans its changes. */
    private void scanColumns() {
        int budget = columnsPerTick;
        while (budget > 0 && nextColumn < columns.length) {
            long column = columns[nextColumn++];
            scanColumn(BlockPos.getX(column), BlockPos.getZ(column));
            budget--;
        }
        if (nextColumn >= columns.length) {
            planned = candidates.stream().sorted(Comparator.comparingDouble(Change::front)).toArray(Change[]::new);
            candidates.clear();
            scanned = true;
            frontStart = Math.max(elapsed, settings.announceTicks());
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
        double front = StormMath.front(x - center.getX(), z - center.getZ(), windX, windZ, settings.radius());
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int bottom = Math.max(level.getMinBuildHeight(), top - EventsConfig.STORM_SCAN_DEPTH.get() + 1);
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            // The roll comes first: most blocks are not candidates, and the checks of a block read its neighbors.
            double chance = EventMath.chance(classChance(BlockClass.of(state)), intensity, settings.strength());
            if (chance > 0.0 && random.nextDouble() < chance && isExposed(pos) && canTear(pos, state)) {
                offer(new Change(pos, state, front));
            }
        }
    }

    /** Keeps a uniform random selection of the candidates, with {@link StormSettings#maxChanges()} at most. */
    private void offer(Change change) {
        seenCandidates++;
        if (candidates.size() < settings.maxChanges()) {
            candidates.add(change);
        } else {
            int index = random.nextInt(seenCandidates);
            if (index < settings.maxChanges()) {
                candidates.set(index, change);
            }
        }
    }

    /** Moves the planned changes that the front reached to the batch queue. */
    private void releaseChanges() {
        if (!scanned) {
            return;
        }
        int frontEnd = settings.announceTicks() + (int) (settings.durationTicks() * FRONT_PART);
        double progress = StormMath.progress(elapsed - frontStart, Math.max(0, frontEnd - frontStart));
        while (nextPlanned < planned.length && planned[nextPlanned].front() <= progress) {
            pending.add(planned[nextPlanned++]);
        }
    }

    /**
     * True if the wind hits a block: a face on the upwind side touches a free position under the open sky. A free
     * position below a roof, a tree or another block is not under the open sky.
     */
    private boolean isExposed(BlockPos pos) {
        for (Direction direction : upwindFaces) {
            BlockPos neighbor = pos.relative(direction);
            if (BlockRules.isFree(level, neighbor) && neighbor.getY() >= level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING, neighbor.getX(), neighbor.getZ())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the wind can tear up a block: a block that an event can change, not the trunk of a natural tree, not the
     * support of a block that must stay, with an item for a block that shatters, and with a free path for a block that
     * flies (see {@link #flightStart}).
     */
    private boolean canTear(BlockPos pos, BlockState state) {
        if (!BlockRules.isChangeable(level, pos, state)
                || (state.is(BlockTags.LOGS) && BlockRules.isTreeTrunk(level, pos))
                || BlockRules.holdsABlock(level, pos)) {
            return false;
        }
        if (state.is(EventBlockTags.SHATTERS)) {
            return BlockRules.isNaturalLeaves(state) || state.getBlock().asItem() != Items.AIR;
        }
        return flightStart(pos).isPresent();
    }

    /**
     * The start of the flight of a torn block: the position of the block when the position on its downwind side is
     * free, else the position above it when this position and the position on its downwind side are free. A block
     * with no free path stays: a collision stops the horizontal motion of a falling block, and the block lands back
     * in its place.
     */
    private Optional<BlockPos> flightStart(BlockPos pos) {
        if (BlockRules.isFree(level, pos.relative(downwind))) {
            return Optional.of(pos);
        }
        BlockPos above = pos.above();
        if (BlockRules.isFree(level, above) && BlockRules.isFree(level, above.relative(downwind))) {
            return Optional.of(above);
        }
        return Optional.empty();
    }

    private boolean apply(Change change) {
        BlockPos pos = change.pos();
        BlockState state = change.expected();
        // The world can change between the examination and the front: the checks apply again.
        if (!isExposed(pos) || !canTear(pos, state)) {
            return false;
        }
        return state.is(EventBlockTags.SHATTERS) ? shatter(pos, state) : launch(pos, state);
    }

    /** A block breaks. Natural leaves drop their loot. The other blocks drop their own item. */
    private boolean shatter(BlockPos pos, BlockState state) {
        if (BlockRules.isNaturalLeaves(state)) {
            return level.destroyBlock(pos, true);
        }
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        if (!level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL)) {
            return false;
        }
        Block.popResource(level, pos, new ItemStack(state.getBlock()));
        return true;
    }

    /** A block flies away with the wind as a falling block, from the start of its flight. */
    private boolean launch(BlockPos pos, BlockState state) {
        Optional<BlockPos> start = flightStart(pos);
        if (start.isEmpty()) {
            return false;
        }
        FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
        BlockPos from = start.get();
        falling.setPos(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        falling.setHurtsEntities(LANDING_DAMAGE, (int) Math.ceil(EventsConfig.STORM_FLYING_DAMAGE.get()));
        double speed = Math.min(MAX_LAUNCH_SPEED,
                (LAUNCH_SPEED + random.nextDouble() * LAUNCH_SPEED_RANDOM) * Math.sqrt(settings.strength()));
        falling.setDeltaMovement(windX * speed, LAUNCH_LIFT + random.nextDouble() * LAUNCH_LIFT_RANDOM,
                windZ * speed);
        falling.hasImpulse = true;
        if (flying.size() < MAX_FLYING) {
            flying.add(new Flying(falling, new IntOpenHashSet()));
        }
        return true;
    }

    /** The flying blocks hurt the living entities that they hit, once for each entity. */
    private void tickFlying() {
        flying.removeIf(block -> !block.entity().isAlive());
        float damage = EventsConfig.STORM_FLYING_DAMAGE.get().floatValue();
        if (damage <= 0.0F) {
            return;
        }
        for (Flying block : flying) {
            FallingBlockEntity entity = block.entity();
            if (!BlockRules.isReady(level, entity.blockPosition())) {
                continue;
            }
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    entity.getBoundingBox().inflate(0.2), target -> target.isAlive() && !target.isSpectator())) {
                if (block.hit().add(target.getId())) {
                    target.hurt(level.damageSources().fallingBlock(entity), damage);
                }
            }
        }
    }

    private boolean isGust() {
        return elapsed < gustEnd;
    }

    /** Starts the gusts at random intervals. A gust pushes the players and throws some mobs into the air. */
    private void gusts() {
        if (elapsed < nextGust) {
            return;
        }
        gustEnd = elapsed + GUST_TICKS;
        nextGust = elapsed + GUST_MIN_INTERVAL + random.nextInt(GUST_MAX_INTERVAL - GUST_MIN_INTERVAL + 1);
        if (EventsConfig.STORM_WIND_PUSH.get() <= 0.0) {
            return;
        }
        double push = PLAYER_GUST * settings.strength();
        for (ServerPlayer player : level.players()) {
            if (isInArea(player) && !player.isSpectator() && !player.isCreative() && !player.getAbilities().flying
                    && BlockRules.isReady(level, player.blockPosition()) && isUnderOpenSky(player)) {
                // The client moves its player: the client adds the push to the motion of the player.
                ModNetwork.send(player, new WindGustPayload((float) (windX * push), 0.05F, (float) (windZ * push)));
            }
        }
        double throwChance = EventsConfig.STORM_THROW_CHANCE.get() * settings.strength();
        for (Mob mob : mobsInArea()) {
            if (mob.onGround() && random.nextDouble() < throwChance) {
                mob.push(windX * THROW_SPEED, THROW_LIFT, windZ * THROW_SPEED);
            }
        }
    }

    /** The wind pushes the mobs and the animals under the open sky. */
    private void pushMobs(double factor) {
        double push = EventsConfig.STORM_WIND_PUSH.get() * settings.strength() * PUSH_INTERVAL * factor;
        if (push <= 0.0) {
            return;
        }
        for (Mob mob : mobsInArea()) {
            mob.push(windX * push, 0.0, windZ * push);
        }
    }

    /**
     * The mobs and the animals that the wind pushes: in the area, in a chunk that ticks, under the open sky, with
     * their AI, not a passenger, and with gravity.
     */
    private List<Mob> mobsInArea() {
        int radius = settings.radius();
        AABB box = new AABB(center.getX() - radius, center.getY() - ENTITY_BELOW, center.getZ() - radius,
                center.getX() + radius + 1, center.getY() + ENTITY_ABOVE, center.getZ() + radius + 1);
        return level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && !mob.isNoAi() && !mob.isPassenger()
                && !mob.isNoGravity() && isInArea(mob) && BlockRules.isReady(level, mob.blockPosition())
                && isUnderOpenSky(mob));
    }

    private boolean isInArea(Entity entity) {
        return horizontalDistanceSquared(entity.getX(), entity.getZ())
                <= (double) settings.radius() * settings.radius();
    }

    /** True if no block that stops the motion is above the eyes of an entity. */
    private boolean isUnderOpenSky(Entity entity) {
        BlockPos eyes = BlockPos.containing(entity.getEyePosition());
        return eyes.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, eyes.getX(), eyes.getZ());
    }

    /** A lightning strike at a random column of the area, with the mean interval of the settings. */
    private void lightning() {
        if (settings.lightningTicks() <= 0 || random.nextInt(settings.lightningTicks()) != 0) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2.0;
        double length = Math.sqrt(random.nextDouble()) * settings.radius();
        BlockPos column = new BlockPos(Mth.floor(center.getX() + Math.cos(angle) * length), center.getY(),
                Mth.floor(center.getZ() + Math.sin(angle) * length));
        if (!BlockRules.isReady(level, column)) {
            return;
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
        BlockPos target = lightningRod(top).orElse(top);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(target));
            level.addFreshEntity(bolt);
            lightningStrikes++;
        }
    }

    /**
     * The position above the nearest lightning rod on the top of its column, near a target. The search occurs only
     * when all the chunks in its range are loaded: the search of the points of interest can load chunks.
     */
    private Optional<BlockPos> lightningRod(BlockPos target) {
        ChunkPos chunk = new ChunkPos(target);
        int chunks = Mth.ceil(LIGHTNING_ROD_RANGE / 16.0);
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                if (!level.hasChunk(chunk.x + dx, chunk.z + dz)) {
                    return Optional.empty();
                }
            }
        }
        return level.getPoiManager().findClosest(type -> type.is(PoiTypes.LIGHTNING_ROD),
                        pos -> BlockRules.isReady(level, pos)
                                && pos.getY() == level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(),
                                        pos.getZ()) - 1,
                        target, LIGHTNING_ROD_RANGE, PoiManager.Occupancy.ANY)
                .map(BlockPos::above);
    }

    private double intensity(int x, int z) {
        double dx = x - center.getX();
        double dz = z - center.getZ();
        return EventMath.intensity(dx * dx + dz * dz, settings.radius());
    }

    private double horizontalDistanceSquared(double x, double z) {
        double dx = x - (center.getX() + 0.5);
        double dz = z - (center.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    private static double classChance(BlockClass blockClass) {
        return switch (blockClass) {
            case FRAGILE -> EventsConfig.STORM_FRAGILE_CHANCE.get();
            case STURDY -> EventsConfig.STORM_STURDY_CHANCE.get();
            case RESISTANT -> EventsConfig.STORM_RESISTANT_CHANCE.get();
        };
    }
}
