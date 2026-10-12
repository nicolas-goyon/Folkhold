package io.github.nicolasgoyon.folkhold.world.entity;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.UnitStats;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.ai.UnitNavigation;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.settlement.Territories;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.Path;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base class of the faction units (guard, archer, captain).
 *
 * <p>A unit has:
 * <ul>
 *   <li>a <b>home position</b>: center of the patrol;</li>
 *   <li>a <b>faction</b>: the faction of its village, or none;</li>
 *   <li>a <b>village</b>: the position of its village heart, or none. The unit reports threats to the heart,
 *       and the heart alerts all units of the village;</li>
 *   <li>a <b>post</b> (optional): a tower or wall position. A unit with a post stays at its post;</li>
 *   <li>a <b>night watch position</b> (optional, set by the heart): at night, the unit stays near it
 *       instead of its home.</li>
 * </ul>
 *
 * <p>A unit chases a target only in its chase area (see {@link #isInChaseArea}). When the unit or its target
 * leaves the area, the unit stops the chase, ignores all targets for {@link #GIVE_UP_TICKS} and goes back. The unit
 * also stops the chase after {@link #UNREACHABLE_TICKS} with no path to the target and no line of sight to it.
 *
 * <p>The attributes come from the config of the unit type when the unit spawns.
 */
public abstract class FactionUnit extends PathfinderMob implements GeoEntity {
    private static final RawAnimation SWING = RawAnimation.begin().thenPlay("attack.swing");
    private static final RawAnimation BLOCK = RawAnimation.begin().thenPlay("attack.block");
    private static final RawAnimation DRAW_BOW = RawAnimation.begin().thenPlay("attack.charge");
    private static final RawAnimation HURT = RawAnimation.begin().thenPlay("misc.hurt");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    /** Color of the tabard (dyed leather chestplate) of a unit without a faction. */
    public static final int DEFAULT_TABARD_COLOR = 0x3C5A9A;

    /** Patrol radius at night, in blocks. */
    private static final int NIGHT_RADIUS = 4;
    private static final String HOME_TAG = "HomePos";
    private static final String VILLAGE_TAG = "VillagePos";
    private static final String POST_TAG = "PostPos";
    private static final String ROUTED_TAG = "RoutedUntil";
    private static final String NIGHT_TAG = "NightPos";
    private static final String FACTION_TAG = "Faction";
    /** Speed of a routed unit that runs back home. */
    private static final double ROUT_SPEED = 1.3;
    /** After the end of a chase at the limit of the chase area, the unit ignores all targets for this time. */
    static final int GIVE_UP_TICKS = 100;
    /** A unit stops the chase after this time with no path to its target and no line of sight to it. */
    static final int UNREACHABLE_TICKS = 200;
    /** Radius around the post while the village is not calm, in blocks. */
    private static final int POST_RADIUS = 2;

    @Nullable
    private BlockPos homePos;
    @Nullable
    private BlockPos villagePos;
    @Nullable
    private BlockPos postPos;
    /** The unit greets a friendly player at most once in this time, in ticks. */
    private static final int GREETING_COOLDOWN = 600;
    private static final double GREETING_DISTANCE = 5.0;
    private long lastGreetingTick = -GREETING_COOLDOWN;
    /** Id of the faction of the unit, or null. */
    @Nullable
    private String factionId;
    /** Position of the night watch (near a gate), or null. The village heart sets it. */
    @Nullable
    private BlockPos nightPos;
    /** Game time until which the unit is routed (runs away and does not fight). */
    private long routedUntil;
    /** Game time until which the unit ignores all targets, after the end of a chase. Not saved. */
    private long gaveUpUntil;
    /** Time in ticks with no path to the target and no line of sight to it. Not saved. */
    private int unreachableTicks;

    protected FactionUnit(EntityType<? extends FactionUnit> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    /** Ground navigation that goes through fence gates and wooden doors (see {@link UnitNavigation}). */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new UnitNavigation(this, level);
    }

    /** The configured values of this unit type. */
    protected abstract UnitStats stats();

    /** Puts the equipment of this unit type in the equipment slots. */
    protected abstract void equip();

    /** The home position. The patrol and the chase limit use it. */
    public BlockPos getHomePos() {
        return homePos != null ? homePos : blockPosition();
    }

    public void setHomePos(BlockPos pos) {
        homePos = pos.immutable();
    }

    /** The position of the village heart, or null if the unit has no village. */
    @Nullable
    public BlockPos getVillagePos() {
        return villagePos;
    }

    public void setVillagePos(@Nullable BlockPos pos) {
        villagePos = pos == null ? null : pos.immutable();
    }

    /** The post of the unit (a tower or a wall position), or null. A unit with a post stays there. */
    @Nullable
    public BlockPos getPostPos() {
        return postPos;
    }

    public void setPostPos(@Nullable BlockPos pos) {
        postPos = pos == null ? null : pos.immutable();
    }

    /**
     * The center of the area where the unit must stay now: the post, else the night watch position at night,
     * else the home position.
     *
     * <p>The unit does not use the vanilla restriction ({@code restrictTo}): vanilla target goals ignore all
     * targets outside the restriction, and a unit on a post must see the targets below its tower.
     */
    public BlockPos getAnchorPos() {
        if (postPos != null) {
            return postPos;
        }
        if (nightPos != null && level().isNight()) {
            return nightPos;
        }
        return getHomePos();
    }

    /** The radius of the area around {@link #getAnchorPos()}, in blocks. */
    public int getAnchorRadius() {
        if (postPos != null) {
            return POST_RADIUS;
        }
        return level().isNight() ? NIGHT_RADIUS : WorldConfig.PATROL_RADIUS.get();
    }

    /** The night watch position (near a gate), or null. At night, the unit stays near it. */
    @Nullable
    public BlockPos getNightPos() {
        return nightPos;
    }

    public void setNightPos(@Nullable BlockPos pos) {
        nightPos = pos == null ? null : pos.immutable();
    }

    /** True if the unit is routed: it runs back home and does not fight. */
    public boolean isRouted() {
        return level().getGameTime() < routedUntil;
    }

    /**
     * Routes the unit: it drops its target, runs back home and does not fight for the given time.
     *
     * @param ticks the duration in game ticks
     */
    public void rout(long ticks) {
        routedUntil = level().getGameTime() + ticks;
        super.setTarget(null);
    }

    /** A routed unit, or a unit that stopped a chase, does not accept a target. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && (isRouted() || level().getGameTime() < gaveUpUntil)) {
            return;
        }
        super.setTarget(target);
    }

    /** True if the unit has a village and the village is not calm. */
    public boolean isVillageAlerted() {
        return getVillageHeart().map(heart -> heart.getAlertState() != AlertState.CALM).orElse(false);
    }

    /** The id of the faction of the unit, or null if the unit has no faction. */
    @Nullable
    public String getFactionId() {
        return factionId;
    }

    public void setFactionId(@Nullable String id) {
        factionId = id;
    }

    /**
     * The reputation tier of a player for this unit: the effective tier in its village, else the tier with its
     * faction. {@link ReputationTier#NEUTRAL} for a unit without a faction.
     */
    public ReputationTier tierOf(Player player) {
        MinecraftServer server = level().getServer();
        if (server == null || factionId == null) {
            return ReputationTier.NEUTRAL;
        }
        return getVillageHeart()
                .map(heart -> Reputations.tier(server, player, heart))
                .orElseGet(() -> Reputations.tier(server, player, factionId));
    }

    /**
     * True if the unit attacks this player on sight: the reputation of the player is hostile, or the player
     * attacked the village of the unit (see {@link VillageHeartBlockEntity#isHostilePlayer}).
     */
    public boolean isHostileTo(Player player) {
        return tierOf(player).isHostile()
                || getVillageHeart().map(heart -> heart.isHostilePlayer(player)).orElse(false);
    }

    /** True if the unit never attacks this player. */
    public boolean isFriendlyTo(Player player) {
        return tierOf(player).isFriendly();
    }

    /** The heart of the village of this unit, if the unit has a village and the heart is loaded. */
    public Optional<VillageHeartBlockEntity> getVillageHeart() {
        if (villagePos == null || !level().isLoaded(villagePos)) {
            return Optional.empty();
        }
        return level().getBlockEntity(villagePos) instanceof VillageHeartBlockEntity heart
                ? Optional.of(heart)
                : Optional.empty();
    }

    /**
     * Tells if the target is in the chase area (see {@link #isInChaseArea}).
     *
     * @param target the possible target
     * @return true if the unit can chase this target
     */
    public boolean canTargetFromHome(LivingEntity target) {
        return isInChaseArea(target.blockPosition());
    }

    /**
     * Tells if a position is in the chase area of the unit:
     * <ul>
     *   <li>not farther than the chase radius ({@link WorldConfig#CHASE_RADIUS}) from the village heart, or from
     *       the home position for a unit without a village;</li>
     *   <li>not in the territory of another faction, for a unit with a faction.</li>
     * </ul>
     *
     * @param pos the position
     * @return true if the position is in the chase area
     */
    public boolean isInChaseArea(BlockPos pos) {
        return isInChaseRadius(pos) && isInTerritory(pos);
    }

    /** True if the position is not farther than the chase radius from the village heart (or the home position). */
    private boolean isInChaseRadius(BlockPos pos) {
        BlockPos center = villagePos != null ? villagePos : getHomePos();
        double chase = WorldConfig.CHASE_RADIUS.get();
        return pos.getCenter().distanceToSqr(center.getCenter()) <= chase * chase;
    }

    /**
     * True if the position is not in the territory of another faction: the territory of the faction of the unit, or
     * an unclaimed cell. Also true if the unit has no faction.
     */
    private boolean isInTerritory(BlockPos pos) {
        MinecraftServer server = level().getServer();
        if (server == null || factionId == null) {
            return true;
        }
        return Territories.factionAt(server, pos).map(faction -> factionId.equals(faction.id())).orElse(true);
    }

    /**
     * True if the unit cannot reach its target: no line of sight, and the path does not go to the target (or
     * the unit has no path and is not near the target).
     */
    private boolean isUnreachable(LivingEntity target) {
        if (getSensing().hasLineOfSight(target)) {
            return false;
        }
        Path path = getNavigation().getPath();
        if (path != null) {
            return !path.canReach();
        }
        return getNavigation().isDone() && distanceToSqr(target) > 9.0;
    }

    /** Stops the chase: the unit drops its target and ignores all targets for {@link #GIVE_UP_TICKS}. */
    private void giveUpChase() {
        super.setTarget(null);
        gaveUpUntil = level().getGameTime() + GIVE_UP_TICKS;
    }

    /**
     * Animation controllers (GeckoLib). The animations are in
     * {@code assets/folkhold_world/animations/entity/faction_unit.animation.json}.
     * <ul>
     *   <li>movement: walk or idle;</li>
     *   <li>attack: wind-up and swing of the weapon arm, when the unit swings;</li>
     *   <li>hold: shield up while the unit blocks, bow drawn while an archer has a target;</li>
     *   <li>hurt: short step back when the unit takes damage.</li>
     * </ul>
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(DefaultAnimations.genericWalkIdleController(this));
        controllers.add(new AnimationController<>(this, "attack", 2, state -> {
            if (swinging) {
                return state.setAndContinue(SWING);
            }
            state.getController().forceAnimationReset();
            return PlayState.STOP;
        }));
        controllers.add(new AnimationController<>(this, "hold", 3, state -> {
            if (isBlocking()) {
                return state.setAndContinue(BLOCK);
            }
            if (isAggressive() && getMainHandItem().getItem() instanceof BowItem) {
                return state.setAndContinue(DRAW_BOW);
            }
            return PlayState.STOP;
        }));
        controllers.add(new AnimationController<>(this, "hurt", 0, state -> {
            if (hurtTime > 0) {
                return state.setAndContinue(HURT);
            }
            state.getController().forceAnimationReset();
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    /**
     * Advances the swing time of the arm. {@code Monster} does this in {@code aiStep}, {@code PathfinderMob}
     * does not. Without it, the attack swing does not play.
     */
    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (isRouted()) {
            if (getTarget() != null) {
                super.setTarget(null);
            }
            if (distanceToSqr(getHomePos().getCenter()) > 9.0 && getNavigation().isDone()) {
                BlockPos home = getHomePos();
                getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, ROUT_SPEED);
            }
            unitServerAiStep(null);
            return;
        }

        LivingEntity target = getTarget();
        if (target instanceof Player player && isFriendlyTo(player)) {
            setTarget(null);
            target = null;
        }
        if (target != null && !target.isAlive()) {
            setTarget(null);
            target = null;
        }
        // The distance check is cheap: each tick. The territory check only each 10 ticks.
        if (target != null && (!isInChaseRadius(target.blockPosition()) || !isInChaseRadius(blockPosition())
                || (tickCount % 10 == 0 && (!isInTerritory(target.blockPosition()) || !isInTerritory(blockPosition()))))) {
            giveUpChase();
            target = null;
        }
        if (target != null && isUnreachable(target)) {
            if (++unreachableTicks > UNREACHABLE_TICKS) {
                giveUpChase();
                target = null;
            }
        } else {
            unreachableTicks = 0;
        }
        if (target != null) {
            LivingEntity threat = target;
            getVillageHeart().ifPresent(heart -> {
                heart.reportThreat(null);
                if (threat instanceof Player player && heart.isHostilePlayer(player)) {
                    heart.markHostile(player);
                }
            });
        } else if (tickCount % 40 == 0) {
            greetFriendlyPlayer();
        }
        unitServerAiStep(target);
    }

    private void greetFriendlyPlayer() {
        if (level().getGameTime() - lastGreetingTick < GREETING_COOLDOWN) {
            return;
        }
        Player player = level().getNearestPlayer(this, GREETING_DISTANCE);
        if (player instanceof ServerPlayer serverPlayer && !player.isSpectator() && isFriendlyTo(player)) {
            lastGreetingTick = level().getGameTime();
            int variant = getRandom().nextInt(3);
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.folkhold_world.greeting." + variant, getDisplayName()), true);
        }
    }

    /**
     * Called on each server AI step, after the common logic.
     *
     * @param target the current target, or null
     */
    protected void unitServerAiStep(@Nullable LivingEntity target) {
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        if (homePos == null) {
            setHomePos(blockPosition());
        }
        if (villagePos == null) {
            VillageHearts.findNearest(level.getLevel(), blockPosition(), WorldConfig.VILLAGE_RADIUS.get())
                    .ifPresent(this::setVillagePos);
        }
        if (factionId == null) {
            getVillageHeart().ifPresent(heart -> {
                heart.ensureSettlement();
                setFactionId(heart.getFactionId());
            });
        }
        applyConfiguredAttributes(stats());
        // The animations use the right arm for the weapon.
        setLeftHanded(false);
        equip();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
        return data;
    }

    /**
     * Sets the base attributes from the config and fills the health.
     *
     * @param stats the configured values of this unit type
     */
    public void applyConfiguredAttributes(UnitStats stats) {
        setBase(Attributes.MAX_HEALTH, stats.health());
        setBase(Attributes.ATTACK_DAMAGE, stats.attackDamage());
        setBase(Attributes.ARMOR, stats.armor());
        setBase(Attributes.MOVEMENT_SPEED, stats.movementSpeed());
        setBase(Attributes.FOLLOW_RANGE, stats.followRange());
        setHealth(getMaxHealth());
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        if (!level().isClientSide()) {
            getVillageHeart().ifPresent(heart -> heart.onUnitDied(getUUID()));
        }
    }

    /** The color of the faction of the unit, or {@link #DEFAULT_TABARD_COLOR} without a faction. */
    protected int factionColor() {
        return faction().map(FactionSpec::color).orElse(DEFAULT_TABARD_COLOR);
    }

    /** The faction of the unit, if it has one and the server knows it. */
    public Optional<FactionSpec> faction() {
        return FactionVisuals.faction(level().getServer(), factionId);
    }

    /** A leather chestplate in the given color. Units wear it as a tabard. */
    protected static ItemStack tabard(int color) {
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color, false));
        return stack;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Units do not pick up items. */
    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (homePos != null) {
            tag.put(HOME_TAG, NbtUtils.writeBlockPos(homePos));
        }
        if (villagePos != null) {
            tag.put(VILLAGE_TAG, NbtUtils.writeBlockPos(villagePos));
        }
        if (postPos != null) {
            tag.put(POST_TAG, NbtUtils.writeBlockPos(postPos));
        }
        tag.putLong(ROUTED_TAG, routedUntil);
        if (nightPos != null) {
            tag.put(NIGHT_TAG, NbtUtils.writeBlockPos(nightPos));
        }
        if (factionId != null) {
            tag.putString(FACTION_TAG, factionId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, HOME_TAG).ifPresent(this::setHomePos);
        NbtUtils.readBlockPos(tag, VILLAGE_TAG).ifPresent(this::setVillagePos);
        NbtUtils.readBlockPos(tag, POST_TAG).ifPresent(this::setPostPos);
        routedUntil = tag.getLong(ROUTED_TAG);
        NbtUtils.readBlockPos(tag, NIGHT_TAG).ifPresent(this::setNightPos);
        factionId = tag.contains(FACTION_TAG) ? tag.getString(FACTION_TAG) : null;
    }
}
