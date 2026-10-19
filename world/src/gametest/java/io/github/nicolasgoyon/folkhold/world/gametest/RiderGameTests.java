package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.entity.FactionHorseEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.entity.RiderEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonRole;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the rider and its horse. See {@link TestSupport} for the templates and the helpers.
 *
 * <p>Tests with riders and monsters use separate batches: a rider can see the monsters of a near test.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RiderGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);

    private RiderGameTests() {
    }

    /** Spawns a rider with no village. The rider gets its horse on its first tick. */
    private static RiderEntity rider(GameTestHelper helper, BlockPos pos) {
        RiderEntity rider = TestSupport.spawnUnit(helper, ModEntities.RIDER.get(), pos);
        rider.setVillagePos(null);
        rider.setFactionId(null);
        rider.setHomePos(helper.absolutePos(pos));
        return rider;
    }

    /** Removes the rider and its horse. */
    private static void remove(RiderEntity rider) {
        FactionHorseEntity horse = rider.getHorse();
        if (horse != null) {
            horse.discard();
        }
        rider.discard();
    }

    /** A new rider gets a saddled horse with a leather armor in the color of its faction, and rides it. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "rider_spawn")
    public static void riderSpawnsOnItsHorse(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        RiderEntity rider = rider(helper, new BlockPos(8, 1, 8));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rider.getVehicle() instanceof FactionHorseEntity,
                        "The rider must ride its horse"))
                .thenExecute(() -> {
                    FactionHorseEntity horse = (FactionHorseEntity) rider.getVehicle();
                    ItemStack armor = horse.getItemBySlot(EquipmentSlot.BODY);
                    DyedItemColor color = armor.get(DataComponents.DYED_COLOR);
                    boolean ownHorse = horse.getUUID().equals(rider.getHorseId())
                            && rider.getUUID().equals(horse.getRiderId());
                    double health = horse.getMaxHealth();
                    boolean saddled = horse.isSaddled();
                    remove(rider);
                    helper.assertTrue(ownHorse, "The rider and its horse must know each other");
                    helper.assertTrue(saddled, "The horse must have a saddle");
                    helper.assertTrue(armor.is(Items.LEATHER_HORSE_ARMOR) && color != null
                            && color.rgb() == FactionUnit.DEFAULT_TABARD_COLOR,
                            "The horse must have a leather armor in the color of the faction");
                    helper.assertTrue(Math.abs(health - WorldConfig.RIDER_HORSE_HEALTH.get()) < 0.001,
                            "The health of the horse must come from the config: " + health);
                })
                .thenSucceed();
    }

    /** A player cannot ride the horse of a rider, or put it on a lead. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "rider_player")
    public static void playerCannotTakeTheHorse(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        RiderEntity rider = rider(helper, new BlockPos(8, 1, 8));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rider.getHorse() != null, "The rider must get its horse"))
                .thenExecute(() -> {
                    FactionHorseEntity horse = rider.getHorse();
                    rider.stopRiding();
                    ServerPlayer player = TestSupport.mockPlayer(helper);
                    player.setGameMode(GameType.SURVIVAL);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_CARROT));
                    InteractionResult result = horse.mobInteract(player, InteractionHand.MAIN_HAND);
                    boolean riding = player.isPassenger();
                    boolean leash = horse.canBeLeashed();
                    helper.getLevel().getServer().getPlayerList().remove(player);
                    remove(rider);
                    helper.assertTrue(result == InteractionResult.PASS && !riding,
                            "A player must not ride or feed the horse");
                    helper.assertTrue(!leash, "A player must not put the horse on a lead");
                })
                .thenSucceed();
    }

    /** A rider on its horse charges a monster near it and kills it. Then it goes back to its patrol. */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "rider_charge")
    public static void riderChargesAMonsterThenPatrols(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        RiderEntity rider = rider(helper, new BlockPos(3, 1, 3));
        // A husk does not burn in daylight.
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(12, 1, 12));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!husk.isAlive(), "The husk is still alive"))
                .thenWaitUntil(() -> helper.assertTrue(rider.isPassenger() && rider.isPatrolling(),
                        "The rider must go back to its patrol on its horse"))
                .thenExecute(() -> remove(rider))
                .thenSucceed();
    }

    /**
     * When its horse dies, the rider gets off and fights on foot. It gets a new horse after the refill delay of the
     * garrison.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "rider_foot")
    public static void riderFightsOnFootWhenItsHorseDies(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        RiderEntity rider = rider(helper, new BlockPos(3, 1, 3));
        UUID[] firstHorse = new UUID[1];
        Husk[] husk = new Husk[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rider.getHorse() != null, "The rider must get its horse"))
                .thenExecute(() -> {
                    FactionHorseEntity horse = rider.getHorse();
                    firstHorse[0] = horse.getUUID();
                    horse.kill();
                    helper.assertTrue(!rider.isPassenger(), "The rider must get off its dead horse");
                    helper.assertTrue(rider.getHorseId() == null, "The rider must know that its horse is dead");
                    husk[0] = helper.spawn(EntityType.HUSK, new BlockPos(10, 1, 10));
                })
                .thenWaitUntil(() -> helper.assertTrue(!husk[0].isAlive(), "The rider on foot must kill the husk"))
                .thenExecute(() -> {
                    helper.assertTrue(!rider.isPassenger(), "The rider must wait for the refill delay");
                    long delay = WorldConfig.GARRISON_REFILL_DELAY_SECONDS.get() * 20L;
                    rider.updateHorse(helper.getLevel().getGameTime() + delay);
                    FactionHorseEntity horse = rider.getHorse();
                    boolean newHorse = rider.getVehicle() == horse && horse != null
                            && !horse.getUUID().equals(firstHorse[0]);
                    remove(rider);
                    helper.assertTrue(newHorse, "After the refill delay, the rider must ride a new horse");
                })
                .thenSucceed();
    }

    /**
     * The units of the village of a horse do not hurt it. An attack of a monster on the horse makes the rider answer
     * the monster. A horse with no rider disappears after a time.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "rider_horse")
    public static void horseOfTheGarrison(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        RiderEntity rider = rider(helper, new BlockPos(3, 1, 3));
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(5, 1, 3));
        guard.setVillagePos(null);
        guard.setFactionId(null);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rider.getHorse() != null, "The rider must get its horse"))
                .thenExecute(() -> {
                    FactionHorseEntity horse = rider.getHorse();
                    Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(12, 1, 12));
                    husk.setNoAi(true);
                    float health = horse.getHealth();
                    horse.hurt(helper.getLevel().damageSources().mobAttack(guard), 4.0F);
                    float afterGuard = horse.getHealth();
                    horse.hurt(helper.getLevel().damageSources().mobAttack(husk), 1.0F);
                    boolean answers = rider.getLastHurtByMob() == husk;
                    rider.discard();
                    long now = helper.getLevel().getGameTime();
                    horse.checkRider(now);
                    horse.checkRider(now + FactionHorseEntity.RIDERLESS_TICKS - 1);
                    boolean staysAWhile = !horse.isRemoved();
                    horse.checkRider(now + FactionHorseEntity.RIDERLESS_TICKS);
                    boolean gone = horse.isRemoved();
                    guard.discard();
                    husk.discard();
                    helper.assertTrue(afterGuard == health, "A guard of the village must not hurt the horse");
                    helper.assertTrue(answers, "The rider must answer an attack on its horse");
                    helper.assertTrue(staysAWhile && gone, "A horse with no rider must disappear after a time");
                })
                .thenSucceed();
    }

    /** A village tier below the town has no rider. A town has one rider, a city two and a capital three. */
    @GameTest(template = "arena", batch = "rider_garrison")
    public static void ridersComeFromTheVillageTier(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        long now = helper.getLevel().getGameTime();
        int[] levels = {8, 9, 13, 17};
        int[] riders = new int[levels.length];
        for (int i = 0; i < levels.length; i++) {
            TestSupport.setLevel(helper, heart, levels[i]);
            heart.ensureGarrison(now + i);
            riders[i] = (int) heart.getUnits().stream().filter(unit -> unit.role() == GarrisonRole.RIDER).count();
        }
        heart.getUnits().forEach(unit -> {
            if (unit instanceof RiderEntity rider) {
                remove(rider);
            } else {
                unit.discard();
            }
        });
        helper.setBlock(HEART, Blocks.AIR);

        helper.assertTrue(riders[0] == 0, "A village must have no rider: " + riders[0]);
        helper.assertTrue(riders[1] == 1, "A town must have one rider: " + riders[1]);
        helper.assertTrue(riders[2] == 2, "A city must have two riders: " + riders[2]);
        helper.assertTrue(riders[3] == 3, "A capital must have three riders: " + riders[3]);
        helper.succeed();
    }

    /**
     * A rider on its horse goes through a gate of the village: the horse finds a path through a closed gate, and in the
     * gate, the rider is not in the wall above the gate.
     */
    @GameTest(template = "village/center", timeoutTicks = 200, batch = "rider_gate")
    public static void riderGoesThroughTheGate(GameTestHelper helper) {
        VillageHeartBlockEntity heart = TestSupport.findHeart(helper);
        TestSupport.setLevel(helper, heart, 9);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        List<FactionUnit> units = heart.getUnits();
        RiderEntity rider = units.stream().filter(RiderEntity.class::isInstance).map(RiderEntity.class::cast)
                .findFirst().orElseThrow();
        units.stream().filter(unit -> unit != rider).forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        Merchants.merchantsOf(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        List<BlockPos> gates = heart.getLayout().gates();
        BlockPos[] gate = new BlockPos[1];

        helper.startSequence()
                // A path needs a horse on the ground: the horse lands first.
                .thenWaitUntil(() -> helper.assertTrue(rider.isPassenger() && rider.getHorse() != null
                        && rider.getHorse().onGround(), "The rider must ride its horse"))
                .thenExecute(() -> {
                    FactionHorseEntity horse = rider.getHorse();
                    gate[0] = middleOfNearestGate(gates, horse.blockPosition());
                    heart.applyDayNight(true);
                    boolean closed = !helper.getLevel().getBlockState(gate[0]).getValue(FenceGateBlock.OPEN);
                    // The floor of the gate is a dirt path, lower than a full block: the path can end one block
                    // lower.
                    Path path = rider.getNavigation().createPath(gate[0], 1);
                    helper.assertTrue(closed, "The gates must be closed for the path search");
                    helper.assertTrue(path != null && path.canReach(), "The horse must find a path through the "
                            + "closed gates to the gate " + gate[0] + " from " + horse.blockPosition());
                    heart.applyDayNight(false);
                    horse.moveTo(gate[0].getX() + 0.5, gate[0].getY(), gate[0].getZ() + 0.5);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    double top = rider.getBoundingBox().maxY;
                    // The opening of a gate is 3 blocks high, from the block of the fence gate.
                    double gateTop = gate[0].getY() + 3.0;
                    boolean inWall = rider.isInWall();
                    remove(rider);
                    helper.assertTrue(!inWall && top <= gateTop,
                            "In the gate, the rider must not be in a wall, and must stay below the top of the gate: "
                                    + top + ", " + gateTop);
                })
                .thenSucceed();
    }

    /**
     * The middle fence gate of the gate that is the nearest to a position. The fence gates of one gate are side by side:
     * a horse fits in the middle of the gate, not in a fence gate at the side.
     */
    private static BlockPos middleOfNearestGate(List<BlockPos> gates, BlockPos pos) {
        BlockPos nearest = gates.stream().min(Comparator.comparingInt(gate -> gate.distManhattan(pos))).orElseThrow();
        List<BlockPos> gate = gates.stream().filter(other -> other.distManhattan(nearest) <= 2)
                .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).toList();
        return gate.get(gate.size() / 2);
    }
}
