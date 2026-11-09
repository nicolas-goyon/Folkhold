package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.FactionGolemEntity;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the faction golem. See {@link TestSupport} for the templates and the helpers.
 *
 * <p>Tests with golems and monsters use separate batches: a golem can see the monsters of a near test.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FactionGolemGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    /** A golem at its post is at this distance from the post or nearer, in blocks. */
    private static final double AT_POST = 3.0;

    private FactionGolemGameTests() {
    }

    /** Spawns a golem with no village. The golem guards a post at its position. */
    private static FactionGolemEntity golemWithPost(GameTestHelper helper, BlockPos relativePost) {
        FactionGolemEntity golem = TestSupport.spawnUnit(helper, ModEntities.FACTION_GOLEM.get(), relativePost);
        golem.setVillagePos(null);
        golem.setFactionId(null);
        BlockPos post = helper.absolutePos(relativePost);
        golem.setHomePos(post);
        golem.setPostPos(post);
        return golem;
    }

    private static List<FactionGolemEntity> golems(VillageHeartBlockEntity heart) {
        return heart.getUnits().stream()
                .filter(FactionGolemEntity.class::isInstance)
                .map(FactionGolemEntity.class::cast)
                .toList();
    }

    /** A new golem has the configured values and no equipment. It does not talk. */
    @GameTest(template = "empty")
    public static void golemSpawnsWithConfiguredValues(GameTestHelper helper) {
        FactionGolemEntity golem = TestSupport.spawnUnit(helper, ModEntities.FACTION_GOLEM.get(), new BlockPos(0, 1, 0));
        double health = WorldConfig.GOLEM.health();
        double maxHealth = golem.getMaxHealth();
        double knockback = golem.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        boolean noEquipment = Arrays.stream(EquipmentSlot.values())
                .allMatch(slot -> golem.getItemBySlot(slot).isEmpty());
        boolean speaks = golem.canSpeak();
        golem.discard();

        helper.assertTrue(Math.abs(maxHealth - health) < 0.001, "Max health must be " + health + ", not " + maxHealth);
        helper.assertTrue(Math.abs(knockback - WorldConfig.GOLEM_KNOCKBACK_RESISTANCE.get()) < 0.001,
                "The knockback resistance must come from the config: " + knockback);
        helper.assertTrue(noEquipment, "A golem has no equipment");
        helper.assertTrue(!speaks, "A golem does not talk");
        helper.succeed();
    }

    /** The attack of a golem hurts the target and throws it up. It starts the attack animation. */
    @GameTest(template = "arena", batch = "golem_hit")
    public static void golemHitThrowsTheTargetUp(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        FactionGolemEntity golem = golemWithPost(helper, new BlockPos(3, 1, 3));
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(5, 1, 3));
        // In the air, the knockback of the hit does not change the vertical speed: only the throw of the golem does.
        husk.setOnGround(false);
        float health = husk.getHealth();
        double speed = husk.getDeltaMovement().y;

        boolean hit = golem.doHurtTarget(husk);

        float healthAfter = husk.getHealth();
        double thrown = husk.getDeltaMovement().y - speed;
        int animation = golem.getAttackAnimationTick();
        golem.discard();
        husk.discard();
        helper.assertTrue(hit && healthAfter < health, "The golem must hurt the husk: " + health + " -> " + healthAfter);
        helper.assertTrue(thrown > 0.3, "The golem must throw the husk up: " + thrown);
        helper.assertTrue(animation == FactionGolemEntity.ATTACK_ANIMATION_TICKS,
                "The hit must start the attack animation: " + animation);
        helper.succeed();
    }

    /**
     * A golem kills a monster near its post, then goes back to its post.
     *
     * <p>The post is at the center of the arena. All the arena is in the chase area of the golem, so the golem does
     * not stop the chase when the husk walks or when a hit pushes the husk. The walls of the test keep the husk in the
     * arena.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "golem_kill")
    public static void golemKillsAMonsterNearItsPost(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockPos relativePost = new BlockPos(8, 1, 8);
        FactionGolemEntity golem = golemWithPost(helper, relativePost);
        BlockPos post = helper.absolutePos(relativePost);
        int last = TestSupport.ARENA_SIZE - 1;
        boolean arenaInChaseArea = Stream.of(new BlockPos(0, 1, 0), new BlockPos(last, 1, 0),
                        new BlockPos(0, 1, last), new BlockPos(last, 1, last), new BlockPos(0, 3, 0))
                .allMatch(corner -> golem.isInChaseArea(helper.absolutePos(corner)));
        helper.assertTrue(arenaInChaseArea, "All the arena must be in the chase area of the golem");
        // A husk does not burn in daylight. It starts near a corner, 7 blocks from the post on each axis.
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(1, 1, 1));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!husk.isAlive(), "The husk is still alive"))
                .thenWaitUntil(() -> helper.assertTrue(golem.blockPosition().closerThan(post, AT_POST),
                        "The golem must go back to its post: " + golem.blockPosition()))
                .thenExecute(golem::discard)
                .thenSucceed();
    }

    /**
     * A golem does not walk into lava to get to a target that it can see but cannot reach. A strip of lava 2 blocks
     * wide goes across all the arena, between the golem and the husk. The husk has no AI, so that it stays near the
     * lava.
     */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "golem_lava")
    public static void golemDoesNotWalkIntoLavaToATarget(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        for (int z = 0; z < TestSupport.ARENA_SIZE; z++) {
            helper.setBlock(new BlockPos(6, 0, z), Blocks.LAVA);
            helper.setBlock(new BlockPos(7, 0, z), Blocks.LAVA);
        }
        FactionGolemEntity golem = golemWithPost(helper, new BlockPos(3, 1, 8));
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(8, 1, 8));
        husk.setNoAi(true);
        // The feet of a golem on the floor are at this height. A golem lower than this fell into the lava strip.
        double floorTop = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
        float health = golem.getMaxHealth();
        boolean[] entered = {false};
        helper.onEachTick(() -> {
            if (golem.isInLava() || golem.getHealth() < health || golem.getY() < floorTop - 0.1) {
                entered[0] = true;
            }
        });

        helper.startSequence()
                .thenExecute(() -> golem.setTarget(husk))
                .thenIdle(150)
                .thenExecute(() -> {
                    LivingEntity target = golem.getTarget();
                    boolean alive = golem.isAlive();
                    golem.discard();
                    husk.discard();
                    for (int z = 0; z < TestSupport.ARENA_SIZE; z++) {
                        helper.setBlock(new BlockPos(6, 0, z), Blocks.SMOOTH_STONE);
                        helper.setBlock(new BlockPos(7, 0, z), Blocks.SMOOTH_STONE);
                    }
                    helper.assertTrue(alive && !entered[0], "The golem must not walk into the lava");
                    helper.assertTrue(target == husk, "The golem must keep the husk as target: it can see it");
                })
                .thenSucceed();
    }

    /**
     * A golem does not chase a target far from its post: it drops the target and stays at its post. The target is in
     * the chase radius of a guard.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "golem_chase")
    public static void golemDoesNotChaseFarFromItsPost(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        FactionGolemEntity golem = golemWithPost(helper, new BlockPos(1, 1, 1));
        BlockPos post = helper.absolutePos(new BlockPos(1, 1, 1));
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(14, 1, 14));
        husk.setNoAi(true);
        double distance = Math.sqrt(post.distSqr(husk.blockPosition()));
        golem.setTarget(husk);

        helper.runAfterDelay(60, () -> {
            LivingEntity target = golem.getTarget();
            boolean atPost = golem.blockPosition().closerThan(post, AT_POST);
            boolean alive = husk.isAlive();
            golem.discard();
            husk.discard();
            helper.assertTrue(distance > WorldConfig.GOLEM_CHASE_RADIUS.get() && distance < WorldConfig.CHASE_RADIUS.get(),
                    "The husk must be outside the chase radius of the golem only: " + distance);
            helper.assertTrue(target == null, "The golem must drop the far target");
            helper.assertTrue(atPost, "The golem must stay at its post: " + golem.blockPosition());
            helper.assertTrue(alive, "The golem must not kill the far target");
            helper.succeed();
        });
    }

    /** A golem at its post near a corner of the arena, and a far husk outside the chase area of the golem. */
    private record GiveUpScene(FactionGolemEntity golem, BlockPos post, Husk far) {
    }

    /**
     * Spawns a golem at its post near a corner of the arena, and a husk with no AI at the opposite corner, outside the
     * chase area of the golem. The golem gets the husk as target. At its next AI step, the golem gives up the chase
     * and ignores all targets for {@link FactionUnit#GIVE_UP_TICKS}.
     */
    private static GiveUpScene giveUpScene(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockPos relativePost = new BlockPos(1, 1, 1);
        FactionGolemEntity golem = golemWithPost(helper, relativePost);
        Husk far = helper.spawn(EntityType.HUSK, new BlockPos(14, 1, 14));
        far.setNoAi(true);
        golem.setTarget(far);
        return new GiveUpScene(golem, helper.absolutePos(relativePost), far);
    }

    /** Asserts that the far husk is outside the chase area of the golem, and that the golem ignores all targets. */
    private static void assertGaveUp(GameTestHelper helper, GiveUpScene scene) {
        FactionGolemEntity golem = scene.golem();
        helper.assertTrue(!golem.isInChaseArea(scene.far().blockPosition()),
                "The far husk must be outside the chase area of the golem");
        helper.assertTrue(golem.isGivingUp(), "The golem must ignore all targets after it gives up a chase");
    }

    /**
     * A golem gives up a chase. Then a monster in the chase area hits the golem: the attack stops the give-up time, and
     * the golem targets the monster. The golem gives up the chase at most 2 ticks before the hit, and the test asserts
     * the target 20 ticks after the hit: before the end of the {@link FactionUnit#GIVE_UP_TICKS}. The monster has no AI
     * and much health, so that it stays at its position and alive.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "golem_give_up_near")
    public static void golemAnswersAnAttackerAfterItGivesUpAChase(GameTestHelper helper) {
        GiveUpScene scene = giveUpScene(helper);
        FactionGolemEntity golem = scene.golem();
        Husk[] near = {null};
        boolean[] answered = {false};
        helper.onEachTick(() -> {
            if (near[0] != null && golem.getTarget() == near[0]) {
                answered[0] = true;
            }
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(golem.getTarget() == null, "The golem must give up the chase"))
                .thenExecute(() -> {
                    assertGaveUp(helper, scene);
                    Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 1, 1));
                    husk.setNoAi(true);
                    AttributeInstance health = husk.getAttribute(Attributes.MAX_HEALTH);
                    if (health != null) {
                        health.setBaseValue(500.0);
                    }
                    husk.setHealth(500.0F);
                    helper.assertTrue(golem.isInChaseArea(golem.blockPosition())
                                    && golem.isInChaseArea(husk.blockPosition()),
                            "The golem and the near husk must be in the chase area of the golem");
                    near[0] = husk;
                    golem.hurt(helper.getLevel().damageSources().mobAttack(husk), 1.0F);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    boolean answer = answered[0];
                    golem.discard();
                    scene.far().discard();
                    near[0].discard();
                    helper.assertTrue(answer, "The golem must target the near husk that hit it, before the end of the"
                            + " give-up time");
                })
                .thenSucceed();
    }

    /**
     * A golem gives up a chase. Then a monster outside the chase area hits the golem: the hit does not stop the give-up
     * time. The golem does not target the monster and stays at its post, until 20 ticks after the end of the give-up
     * time.
     */
    @GameTest(template = "arena", timeoutTicks = 300, batch = "golem_give_up_far")
    public static void golemDoesNotChaseAFarAttackerAfterItGivesUpAChase(GameTestHelper helper) {
        GiveUpScene scene = giveUpScene(helper);
        FactionGolemEntity golem = scene.golem();
        boolean[] chased = {false};
        boolean[] hit = {false};
        helper.onEachTick(() -> {
            if (hit[0] && (golem.getTarget() == scene.far() || !golem.blockPosition().closerThan(scene.post(), AT_POST))) {
                chased[0] = true;
            }
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(golem.getTarget() == null, "The golem must give up the chase"))
                .thenExecute(() -> {
                    assertGaveUp(helper, scene);
                    golem.hurt(helper.getLevel().damageSources().mobAttack(scene.far()), 1.0F);
                    helper.assertTrue(golem.getLastHurtByMob() == scene.far(),
                            "The far husk must be the attacker of the golem");
                    helper.assertTrue(golem.isGivingUp(),
                            "The hit of the far husk must not stop the give-up time of the golem");
                    hit[0] = true;
                })
                .thenIdle(FactionUnit.GIVE_UP_TICKS + 20)
                .thenExecute(() -> {
                    boolean chase = chased[0];
                    golem.discard();
                    scene.far().discard();
                    helper.assertTrue(!chase, "The golem must not chase the far husk that hit it");
                })
                .thenSucceed();
    }

    /**
     * A village has no golem. A town has one golem, and a city and a capital two golems, with an armband in the faction
     * color.
     */
    @GameTest(template = "arena", batch = "golem_garrison")
    public static void golemsComeFromTheVillageTier(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        long now = helper.getLevel().getGameTime();
        TestSupport.setLevel(helper, heart, 8);
        heart.ensureGarrison(now);
        int village = golems(heart).size();
        TestSupport.setLevel(helper, heart, 12);
        heart.ensureGarrison(now + 1);
        int town = golems(heart).size();
        TestSupport.setLevel(helper, heart, 13);
        heart.ensureGarrison(now + 2);
        int city = golems(heart).size();
        TestSupport.setLevel(helper, heart, 17);
        heart.ensureGarrison(now + 3);
        List<FactionGolemEntity> capital = golems(heart);
        boolean armbands = capital.stream().allMatch(golem -> golem.getArmbandColor()
                == golem.faction().map(FactionSpec::color).orElse(FactionUnit.DEFAULT_TABARD_COLOR));
        heart.getUnits().forEach(Entity::discard);
        helper.setBlock(HEART, Blocks.AIR);

        helper.assertTrue(village == 0, "A village must have no golem: " + village);
        helper.assertTrue(town == 1, "A town must have one golem: " + town);
        helper.assertTrue(city == 2, "A city must have two golems: " + city);
        helper.assertTrue(capital.size() == 2, "A capital must have two golems: " + capital.size());
        helper.assertTrue(armbands, "The armband of a golem must have the color of its faction");
        helper.succeed();
    }

    /**
     * In a capital, each golem guards a gate. Its post is inside the gate, toward the heart, and the golem can walk
     * through the open gate. The golem attacks a monster that comes to the gate. The other units of the village go
     * away first: the hit must come from a golem. The monster has no AI, so that it stays in the test area.
     */
    @GameTest(template = "village/center", timeoutTicks = 400, batch = "golem_gates")
    public static void golemsGuardTheGates(GameTestHelper helper) {
        VillageHeartBlockEntity heart = TestSupport.findHeart(helper);
        TestSupport.setLevel(helper, heart, 17);
        heart.ensureGarrison(helper.getLevel().getGameTime());
        heart.applyDayNight(false);
        heart.getUnits().stream().filter(unit -> !(unit instanceof FactionGolemEntity)).forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
        List<FactionGolemEntity> golems = golems(heart);
        List<BlockPos> posts = heart.golemPosts();
        List<BlockPos> gates = heart.getLayout().gates();
        boolean atPosts = golems.size() == 2 && golems.stream().allMatch(golem -> posts.contains(golem.getPostPos())
                && golem.blockPosition().equals(golem.getPostPos()))
                && !golems.get(0).getPostPos().equals(golems.get(1).getPostPos());
        boolean insideGates = posts.stream().allMatch(post -> {
            BlockPos gate = nearestGate(gates, post);
            return gate.distManhattan(post) == 4
                    && post.distSqr(heart.getBlockPos()) < gate.distSqr(heart.getBlockPos());
        });
        boolean fitsInGates = posts.stream().allMatch(post -> FactionGolemEntity.fitsAt(helper.getLevel(),
                nearestGate(gates, post)));

        helper.assertTrue(posts.size() == 2, "Expected 2 golem posts, one for each gate, found " + posts);
        helper.assertTrue(atPosts, "Each of the 2 golems must stand on its own post: " + golems.stream()
                .map(FactionGolemEntity::getPostPos).toList());
        helper.assertTrue(insideGates, "Each post must be 4 blocks inside a gate: " + posts);
        helper.assertTrue(fitsInGates, "A golem must fit in each open gate");
        FactionGolemEntity golem = golems.get(0);
        BlockPos gate = nearestGate(gates, golem.getPostPos());
        Husk[] husk = new Husk[1];
        helper.startSequence()
                // A path needs a golem on the ground: the golem lands first.
                .thenIdle(5)
                .thenExecute(() -> {
                    // The floor of the gate is a dirt path, lower than a full block: the path can end one block
                    // lower.
                    Path path = golem.getNavigation().createPath(gate, 1);
                    helper.assertTrue(path != null && path.canReach(), "A golem must find a path to its gate "
                            + gate + " from " + golem.blockPosition());
                    // The test spawns the husk at the absolute position of the gate: the relative positions of
                    // the helper and the positions of the template blocks do not always agree.
                    husk[0] = EntityType.HUSK.spawn(helper.getLevel(), gate, MobSpawnType.COMMAND);
                    helper.assertTrue(husk[0] != null, "The husk must spawn at the gate");
                    // The husk stays at the gate. The gate is one block from the edge of the template, and the
                    // entities tick only in the chunks of the test area. A husk that walks out of the gate can go
                    // into a chunk that does not tick: the husk and the golem that follows it stop there.
                    husk[0].setNoAi(true);
                })
                .thenWaitUntil(() -> helper.assertTrue(husk[0].getLastHurtByMob() == golem || !husk[0].isAlive(),
                        "The golem must hit the husk at the gate"))
                .thenExecute(() -> {
                    husk[0].discard();
                    heart.getUnits().forEach(Entity::discard);
                })
                .thenSucceed();
    }

    /** The gate block that is the nearest to a position. */
    private static BlockPos nearestGate(List<BlockPos> gates, BlockPos pos) {
        return gates.stream().min(Comparator.comparingInt(gate -> gate.distManhattan(pos))).orElseThrow();
    }

    /**
     * In a village with golems, the villagers do not call a vanilla iron golem, and the villagers near the call act as
     * if they saw a golem. In a hamlet, the call works.
     */
    @GameTest(template = "arena", batch = "golem_vanilla")
    public static void golemVillageHasNoVanillaIronGolem(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        // The test registers the heart: the calls of the test come before the first tick of the heart.
        VillageHearts.register(helper.getLevel(), heart.getBlockPos());
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
        BlockPos call = helper.absolutePos(new BlockPos(12, 1, 12));

        TestSupport.setLevel(helper, heart, 13);
        Optional<IronGolem> city = SpawnUtil.trySpawnMob(EntityType.IRON_GOLEM, MobSpawnType.MOB_SUMMONED,
                helper.getLevel(), call, 1, 0, 0, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER);
        boolean sawGolem = villager.getBrain().hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
        TestSupport.setLevel(helper, heart, 1);
        Optional<IronGolem> hamlet = SpawnUtil.trySpawnMob(EntityType.IRON_GOLEM, MobSpawnType.MOB_SUMMONED,
                helper.getLevel(), call, 1, 0, 0, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER);
        city.ifPresent(Entity::discard);
        hamlet.ifPresent(Entity::discard);
        villager.discard();
        helper.setBlock(HEART, Blocks.AIR);

        helper.assertTrue(city.isEmpty(), "The villagers of a city must not call a vanilla iron golem");
        helper.assertTrue(sawGolem, "The villagers near the call must act as if they saw a golem");
        helper.assertTrue(hamlet.isPresent(), "The villagers of a hamlet must call a vanilla iron golem");
        helper.succeed();
    }
}
