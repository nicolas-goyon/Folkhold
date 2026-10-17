package io.github.nicolasgoyon.folkhold.world.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration of the world mod: file {@code folkhold_world-server.toml} in the {@code serverconfig}
 * folder of each world.
 *
 * <p>The values apply to units that spawn after the change. Existing units keep their values.
 */
public final class WorldConfig {
    static final String KEY_PREFIX = "folkhold_world.configuration.";

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue HEALTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;

    public static final ModConfigSpec.IntValue PATROL_RADIUS;
    public static final ModConfigSpec.IntValue CHASE_RADIUS;
    public static final ModConfigSpec.IntValue ALERT_RADIUS;
    public static final ModConfigSpec.BooleanValue MONSTERS_ATTACK_GUARDS;

    public static final ModConfigSpec.IntValue VILLAGE_RADIUS;
    public static final ModConfigSpec.IntValue VILLAGE_MESSAGE_RADIUS;
    public static final ModConfigSpec.IntValue SPAWN_PROTECTION_RADIUS;
    public static final ModConfigSpec.IntValue CALM_DELAY_SECONDS;

    public static final ModConfigSpec.IntValue GARRISON_GUARDS;
    public static final ModConfigSpec.IntValue GARRISON_ARCHERS;
    public static final ModConfigSpec.IntValue GARRISON_CAPTAINS;

    public static final ModConfigSpec.IntValue CAPTAIN_AURA_RADIUS;
    public static final ModConfigSpec.DoubleValue CAPTAIN_ROUT_SHARE;
    public static final ModConfigSpec.IntValue CAPTAIN_ROUT_SECONDS;
    public static final ModConfigSpec.IntValue GARRISON_REFILL_DELAY_SECONDS;
    public static final ModConfigSpec.IntValue GARRISON_ACTIVATION_RADIUS;
    public static final ModConfigSpec.IntValue MAX_INHABITANTS;
    public static final ModConfigSpec.IntValue ARCHER_ROTATION_SECONDS;

    public static final ModConfigSpec.DoubleValue REPUTATION_VILLAGE_WEIGHT;
    public static final ModConfigSpec.DoubleValue REPUTATION_ALLY_SPREAD;
    public static final ModConfigSpec.DoubleValue REPUTATION_ENEMY_SPREAD;
    public static final ModConfigSpec.IntValue REPUTATION_KILL_UNIT;
    public static final ModConfigSpec.IntValue REPUTATION_HIT_UNIT;
    public static final ModConfigSpec.IntValue REPUTATION_KILL_MONSTER;
    public static final ModConfigSpec.IntValue REPUTATION_OFFENSE;
    public static final ModConfigSpec.IntValue REPUTATION_OFFENSE_WINDOW_SECONDS;
    public static final ModConfigSpec.IntValue REPUTATION_DESTROY_VILLAGE;
    public static final ModConfigSpec.IntValue REPUTATION_HIT_CAT;
    public static final ModConfigSpec.IntValue REPUTATION_KILL_CAT;
    public static final ModConfigSpec.IntValue REPUTATION_RAID_CARAVAN;

    public static final ModConfigSpec.IntValue DISCOVERY_RADIUS;
    public static final ModConfigSpec.IntValue DISCOVERY_CHECKS_PER_TICK;
    public static final ModConfigSpec.IntValue SIMULATION_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue SIMULATION_CYCLE_LENGTH;
    public static final ModConfigSpec.DoubleValue SIMULATION_BUDGET_MS;
    public static final ModConfigSpec.DoubleValue LEVEL_BASE_PROSPERITY;
    public static final ModConfigSpec.IntValue LEVEL_MAX_GAIN_PER_CYCLE;
    public static final ModConfigSpec.IntValue LEVEL_CAPTAIN_DEATH_LOSS;
    public static final ModConfigSpec.IntValue ECONOMY_WORKERS_PER_JOB;
    public static final ModConfigSpec.IntValue ECONOMY_FOOD_PER_INHABITANT;
    public static final ModConfigSpec.DoubleValue ECONOMY_DECAY_RATE;
    public static final ModConfigSpec.DoubleValue ECONOMY_ELASTICITY;
    public static final ModConfigSpec.IntValue ECONOMY_START_TREASURY;
    public static final ModConfigSpec.IntValue ECONOMY_TREASURY_MINIMUM;
    public static final ModConfigSpec.IntValue ECONOMY_FOOD_SHORTAGE_LOSS;

    public static final ModConfigSpec.IntValue GROUP_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue GROUP_MAX_GROUPS;
    public static final ModConfigSpec.IntValue GROUP_MATERIALIZE_DISTANCE;
    public static final ModConfigSpec.IntValue GROUP_DEMATERIALIZE_MARGIN;
    public static final ModConfigSpec.IntValue ROUTE_STEP;
    public static final ModConfigSpec.IntValue ROUTE_MAX_LENGTH;
    public static final ModConfigSpec.IntValue ROUTE_MAX_NODES;
    public static final ModConfigSpec.BooleanValue ROUTE_BOATS;
    public static final ModConfigSpec.IntValue ROUTE_DETAIL_STEP;
    public static final ModConfigSpec.IntValue ROUTE_MAX_RISE;
    public static final ModConfigSpec.IntValue ROUTE_CORRIDOR;
    public static final ModConfigSpec.IntValue ROUTE_DETAIL_MAX_NODES;
    public static final ModConfigSpec.IntValue TRADE_CARAVANS_PER_TICK;
    public static final ModConfigSpec.IntValue TRADE_CARAVAN_CAPACITY;
    public static final ModConfigSpec.IntValue TRADE_ROUTES_PER_TICK;
    public static final ModConfigSpec.IntValue TRADE_RELATION_GAIN;
    public static final ModConfigSpec.IntValue TRAVELING_MERCHANTS;
    public static final ModConfigSpec.IntValue TRAVELING_STAY_TICKS;

    public static final ModConfigSpec.IntValue QUEST_OFFER_MINUTES;
    public static final ModConfigSpec.IntValue QUEST_DELIVERY_MINUTES;
    public static final ModConfigSpec.DoubleValue QUEST_DELIVERY_BONUS;
    public static final ModConfigSpec.IntValue QUEST_DELIVERY_REPUTATION;
    public static final ModConfigSpec.IntValue QUEST_DELIVERY_MAX_COUNT;
    public static final ModConfigSpec.IntValue QUEST_FRIEND_REWARD;
    public static final ModConfigSpec.IntValue QUEST_MAX_ACTIVE;
    public static final ModConfigSpec.BooleanValue QUEST_ESCORT_OFFERS;
    public static final ModConfigSpec.IntValue QUEST_ESCORT_WAIT_TICKS;
    public static final ModConfigSpec.IntValue QUEST_ESCORT_DISTANCE;
    public static final ModConfigSpec.IntValue QUEST_ESCORT_AWAY_SECONDS;
    public static final ModConfigSpec.IntValue QUEST_ESCORT_MINUTES;
    public static final ModConfigSpec.IntValue QUEST_ESCORT_BLOCKS_PER_MINUTE;
    public static final ModConfigSpec.DoubleValue QUEST_ESCORT_REWARD_SHARE;
    public static final ModConfigSpec.IntValue QUEST_ESCORT_REPUTATION;

    public static final ModConfigSpec.DoubleValue MERCHANT_FRIEND_FACTOR;
    public static final ModConfigSpec.DoubleValue MERCHANT_ALLY_FACTOR;
    public static final ModConfigSpec.IntValue BANK_PILLAGE_VALUE;
    public static final ModConfigSpec.IntValue BANK_PILLAGE_LOSS;
    public static final ModConfigSpec.DoubleValue BANK_TREASURY_SHARE;

    public static final UnitStats GUARD;
    public static final UnitStats ARCHER;
    public static final UnitStats CAPTAIN;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Global difficulty of the faction units.")
                .translation(KEY_PREFIX + "difficulty")
                .push("difficulty");
        HEALTH_MULTIPLIER = builder
                .comment("Multiplier for the health of all faction units.")
                .translation(KEY_PREFIX + "healthMultiplier")
                .defineInRange("healthMultiplier", 1.0, 0.1, 10.0);
        DAMAGE_MULTIPLIER = builder
                .comment("Multiplier for the attack damage of all faction units.")
                .translation(KEY_PREFIX + "damageMultiplier")
                .defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
        builder.pop();

        builder.comment("Behavior of the village defenders.")
                .translation(KEY_PREFIX + "ai")
                .push("ai");
        PATROL_RADIUS = builder
                .comment("Radius in blocks of the patrol area around the home position.")
                .translation(KEY_PREFIX + "patrolRadius")
                .defineInRange("patrolRadius", 16, 2, 128);
        CHASE_RADIUS = builder
                .comment("Radius in blocks of the chase area around the village heart (around the home position for a",
                        "unit without a village). A unit chases a target only in this area, and not in the territory of",
                        "another faction. When the unit or its target leaves the area, the unit stops the chase and goes",
                        "back.")
                .translation(KEY_PREFIX + "chaseRadius")
                .defineInRange("chaseRadius", 64, 4, 200);
        ALERT_RADIUS = builder
                .comment("Radius in blocks in which an attack on one unit alerts the other units.")
                .translation(KEY_PREFIX + "alertRadius")
                .defineInRange("alertRadius", 24, 4, 128);
        MONSTERS_ATTACK_GUARDS = builder
                .comment("Hostile monsters attack guards first, as they attack iron golems.",
                        "The entity types in the tag folkhold_world:ignores_guards never do.",
                        "Applies to monsters that spawn or load after the change.")
                .translation(KEY_PREFIX + "monstersAttackGuards")
                .define("monstersAttackGuards", true);
        builder.pop();

        builder.comment("Faction villages.")
                .translation(KEY_PREFIX + "village")
                .push("village");
        VILLAGE_RADIUS = builder
                .comment("Radius in blocks of a village around its heart. Units in this radius join the village",
                        "when they spawn. An alert reaches all units of the village in this radius. The territory of",
                        "a village has a radius of at least 1.5 times this radius.")
                .translation(KEY_PREFIX + "villageRadius")
                .defineInRange("radius", 48, 8, 256);
        VILLAGE_MESSAGE_RADIUS = builder
                .comment("Distance in blocks from a village heart where a player gets the village message",
                        "(village name, faction, reputation). The village wall is at approximately 20 blocks.")
                .translation(KEY_PREFIX + "villageMessageRadius")
                .defineInRange("messageRadius", 50, 8, 256);
        SPAWN_PROTECTION_RADIUS = builder
                .comment("Radius in blocks around a village heart where hostile monsters do not spawn naturally.",
                        "0 turns off the protection.")
                .translation(KEY_PREFIX + "spawnProtectionRadius")
                .defineInRange("spawnProtectionRadius", 48, 0, 256);
        CALM_DELAY_SECONDS = builder
                .comment("Time in seconds without a threat before an alerted village becomes calm again.")
                .translation(KEY_PREFIX + "calmDelaySeconds")
                .defineInRange("calmDelaySeconds", 60, 5, 3600);
        builder.pop();

        builder.comment("Garrison of each village.")
                .translation(KEY_PREFIX + "garrison")
                .push("garrison");
        GARRISON_GUARDS = builder
                .comment("Number of guards in a full garrison.")
                .translation(KEY_PREFIX + "garrisonGuards")
                .defineInRange("guards", 2, 0, 32);
        GARRISON_ARCHERS = builder
                .comment("Number of archers in a full garrison.")
                .translation(KEY_PREFIX + "garrisonArchers")
                .defineInRange("archers", 2, 0, 32);
        GARRISON_CAPTAINS = builder
                .comment("Number of captains in a full garrison.")
                .translation(KEY_PREFIX + "garrisonCaptains")
                .defineInRange("captains", 1, 0, 4);
        GARRISON_REFILL_DELAY_SECONDS = builder
                .comment("Time in seconds before the village spawns a new unit for a dead one. The village must be calm.")
                .translation(KEY_PREFIX + "garrisonRefillDelaySeconds")
                .defineInRange("refillDelaySeconds", 300, 10, 86400);
        GARRISON_ACTIVATION_RADIUS = builder
                .comment("A heart creates its missing units only when a player is in this radius, in blocks.")
                .translation(KEY_PREFIX + "garrisonActivationRadius")
                .defineInRange("activationRadius", 64, 16, 256);
        MAX_INHABITANTS = builder
                .comment("Maximum number of inhabitants (villagers with no trades) in the world for each village.",
                        "The number comes from the population of the settlement, minus the merchants.")
                .translation(KEY_PREFIX + "maxInhabitants")
                .defineInRange("maxInhabitants", 6, 0, 64);
        ARCHER_ROTATION_SECONDS = builder
                .comment("Mean time in seconds that a calm archer stays on one tower before it moves to another tower.",
                        "0 turns off the rotation.")
                .translation(KEY_PREFIX + "archerRotationSeconds")
                .defineInRange("archerRotationSeconds", 120, 0, 3600);
        builder.pop();

        builder.comment("Captain: morale aura and effect of its death.")
                .translation(KEY_PREFIX + "captain")
                .push("captain");
        CAPTAIN_AURA_RADIUS = builder
                .comment("Radius in blocks of the morale aura (Strength I for the units of the village).")
                .translation(KEY_PREFIX + "captainAuraRadius")
                .defineInRange("auraRadius", 16, 0, 64);
        CAPTAIN_ROUT_SHARE = builder
                .comment("Share of the units of the village that run away when the captain dies (0 to 1).")
                .translation(KEY_PREFIX + "captainRoutShare")
                .defineInRange("routShare", 0.3, 0.0, 1.0);
        CAPTAIN_ROUT_SECONDS = builder
                .comment("Duration in seconds of the rout and of the Weakness effect after the death of the captain.")
                .translation(KEY_PREFIX + "captainRoutSeconds")
                .defineInRange("routSeconds", 20, 1, 600);
        builder.pop();

        builder.comment("Reputation of the players with the factions and the villages (-1000 to +1000).")
                .translation(KEY_PREFIX + "reputation")
                .push("reputation");
        REPUTATION_VILLAGE_WEIGHT = builder
                .comment("Share of the village reputation in the effective reputation. The faction has the rest.")
                .translation(KEY_PREFIX + "reputationVillageWeight")
                .defineInRange("villageWeight", 0.7, 0.0, 1.0);
        REPUTATION_ALLY_SPREAD = builder
                .comment("Share of a change that also applies to the allies of the faction.")
                .translation(KEY_PREFIX + "reputationAllySpread")
                .defineInRange("allySpread", 0.5, 0.0, 1.0);
        REPUTATION_ENEMY_SPREAD = builder
                .comment("Share of a change that applies, with the opposite sign, to the enemies of the faction.")
                .translation(KEY_PREFIX + "reputationEnemySpread")
                .defineInRange("enemySpread", 0.3, 0.0, 1.0);
        REPUTATION_KILL_UNIT = builder
                .comment("Change when the player kills a unit of the faction.")
                .translation(KEY_PREFIX + "reputationKillUnit")
                .defineInRange("killUnit", -150, -1000, 0);
        REPUTATION_HIT_UNIT = builder
                .comment("Change when the player hits a unit of the faction.")
                .translation(KEY_PREFIX + "reputationHitUnit")
                .defineInRange("hitUnit", -20, -1000, 0);
        REPUTATION_KILL_MONSTER = builder
                .comment("Change when the player kills a hostile monster in the radius of a village.")
                .translation(KEY_PREFIX + "reputationKillMonster")
                .defineInRange("killMonsterNearVillage", 5, 0, 1000);
        REPUTATION_OFFENSE = builder
                .comment("Change when a neutral player breaks a block or opens a container in a village.")
                .translation(KEY_PREFIX + "reputationOffense")
                .defineInRange("offense", -10, -1000, 0);
        REPUTATION_OFFENSE_WINDOW_SECONDS = builder
                .comment("Time in seconds in which a second offense makes the units attack.",
                        "After this time without an offense, the next offense gives a warning again.")
                .translation(KEY_PREFIX + "reputationOffenseWindowSeconds")
                .defineInRange("offenseWindowSeconds", 60, 5, 3600);
        REPUTATION_DESTROY_VILLAGE = builder
                .comment("Change for the player who destroys a village of the faction (the last action: the death of",
                        "the captain or the destruction of the village heart).")
                .translation(KEY_PREFIX + "reputationDestroyVillage")
                .defineInRange("destroyVillage", -400, -2000, 0);
        REPUTATION_HIT_CAT = builder
                .comment("Change when the player hits a cat of a village of the faction.")
                .translation(KEY_PREFIX + "reputationHitCat")
                .defineInRange("hitCat", -10, -1000, 0);
        REPUTATION_KILL_CAT = builder
                .comment("Change when the player kills a cat of a village of the faction.")
                .translation(KEY_PREFIX + "reputationKillCat")
                .defineInRange("killCat", -50, -1000, 0);
        REPUTATION_RAID_CARAVAN = builder
                .comment("Reputation change with the faction of a caravan when a player raids it (hurts a member or",
                        "opens a mule chest). Once in each offense window.")
                .translation(KEY_PREFIX + "reputationRaidCaravan")
                .defineInRange("raidCaravan", -150, -1000, 0);
        builder.pop();

        builder.comment("Abstract simulation of the settlements.")
                .translation(KEY_PREFIX + "simulation")
                .push("simulation");
        DISCOVERY_RADIUS = builder
                .comment("Radius in blocks around each player and around the world spawn where the mod finds the",
                        "villages before the game generates their chunks.")
                .translation(KEY_PREFIX + "discoveryRadius")
                .defineInRange("discoveryRadius", 1024, 128, 8192);
        DISCOVERY_CHECKS_PER_TICK = builder
                .comment("Maximum number of placement regions that the discovery checks in each server tick.")
                .translation(KEY_PREFIX + "discoveryChecksPerTick")
                .defineInRange("discoveryChecksPerTick", 2, 1, 64);
        SIMULATION_INTERVAL_TICKS = builder
                .comment("Game ticks between two abstract ticks of the simulation (2400 = 2 minutes).")
                .translation(KEY_PREFIX + "simulationIntervalTicks")
                .defineInRange("intervalTicks", 2400, 20, 72000);
        SIMULATION_CYCLE_LENGTH = builder
                .comment("Abstract ticks in one cycle. Production and consumption run once each cycle.",
                        "With the default interval, 10 abstract ticks are one game day.")
                .translation(KEY_PREFIX + "simulationCycleLength")
                .defineInRange("cycleLength", 10, 1, 1000);
        SIMULATION_BUDGET_MS = builder
                .comment("Maximum time in milliseconds of the simulation in one server tick. When the time is spent,",
                        "the simulation continues with the remaining settlements at the next server tick.")
                .translation(KEY_PREFIX + "simulationBudgetMs")
                .defineInRange("budgetMs", 5.0, 0.1, 50.0);
        LEVEL_BASE_PROSPERITY = builder
                .comment("Prosperity of a settlement (0 to 1) when no system of the simulation gives a prosperity.")
                .translation(KEY_PREFIX + "levelBaseProsperity")
                .defineInRange("baseProsperity", 0.5, 0.0, 1.0);
        LEVEL_MAX_GAIN_PER_CYCLE = builder
                .comment("Experience that a settlement gets in one cycle with a full prosperity.",
                        "The levels and the experience for each level are in the data file folkhold/levels.json.")
                .translation(KEY_PREFIX + "levelMaxGainPerCycle")
                .defineInRange("maxExperiencePerCycle", 10, 0, 1000);
        LEVEL_CAPTAIN_DEATH_LOSS = builder
                .comment("Experience that a settlement loses when its captain dies.")
                .translation(KEY_PREFIX + "levelCaptainDeathLoss")
                .defineInRange("captainDeathLoss", 30, 0, 10000);
        builder.pop();

        builder.comment("Economy of the settlements. The jobs are in the data files folkhold/jobs/*.json and the item",
                        "values in folkhold/items.json.")
                .translation(KEY_PREFIX + "economy")
                .push("economy");
        ECONOMY_WORKERS_PER_JOB = builder
                .comment("Inhabitants for one active job.")
                .translation(KEY_PREFIX + "economyWorkersPerJob")
                .defineInRange("workersPerJob", 2, 1, 20);
        ECONOMY_FOOD_PER_INHABITANT = builder
                .comment("Food points that one inhabitant eats in one cycle (bread: 5 points).")
                .translation(KEY_PREFIX + "economyFoodPerInhabitant")
                .defineInRange("foodPerInhabitant", 4, 0, 100);
        ECONOMY_DECAY_RATE = builder
                .comment("Share of each stock that rots or wears in one cycle.")
                .translation(KEY_PREFIX + "economyDecayRate")
                .defineInRange("decayRate", 0.02, 0.0, 1.0);
        ECONOMY_ELASTICITY = builder
                .comment("Elasticity of the prices. 0: the price does not change with the stock.")
                .translation(KEY_PREFIX + "economyElasticity")
                .defineInRange("elasticity", 0.5, 0.0, 3.0);
        ECONOMY_START_TREASURY = builder
                .comment("Emeralds in the treasury of a faction at the start.")
                .translation(KEY_PREFIX + "economyStartTreasury")
                .defineInRange("startTreasury", 1000, 0, 1_000_000);
        ECONOMY_TREASURY_MINIMUM = builder
                .comment("A faction with fewer emeralds in its treasury gives less prosperity to its settlements.")
                .translation(KEY_PREFIX + "economyTreasuryMinimum")
                .defineInRange("treasuryMinimum", 100, 0, 1_000_000);
        ECONOMY_FOOD_SHORTAGE_LOSS = builder
                .comment("Experience that a settlement loses in a cycle with a food shortage (less than half of its need).")
                .translation(KEY_PREFIX + "economyFoodShortageLoss")
                .defineInRange("foodShortageLoss", 10, 0, 10000);
        builder.pop();

        builder.comment("Mobile groups: caravans and traveling merchants.")
                .translation(KEY_PREFIX + "groups")
                .push("groups");
        GROUP_BLOCKS_PER_TICK = builder
                .comment("Distance in blocks that a mobile group with no entities moves on each abstract tick.")
                .translation(KEY_PREFIX + "groupBlocksPerTick")
                .defineInRange("blocksPerTick", 200, 1, 10000);
        GROUP_MAX_GROUPS = builder
                .comment("Maximum number of mobile groups in the world. When the world has this number of groups,",
                        "the simulation makes no new group.")
                .translation(KEY_PREFIX + "groupMaxGroups")
                .defineInRange("maxGroups", 32, 0, 1024);
        GROUP_MATERIALIZE_DISTANCE = builder
                .comment("Horizontal distance in blocks from a player where a mobile group gets its entities.")
                .translation(KEY_PREFIX + "groupMaterializeDistance")
                .defineInRange("materializeDistance", 112, 32, 512);
        GROUP_DEMATERIALIZE_MARGIN = builder
                .comment("A mobile group loses its entities when no player is nearer than the materialization",
                        "distance plus this margin, in blocks.")
                .translation(KEY_PREFIX + "groupDematerializeMargin")
                .defineInRange("dematerializeMargin", 32, 8, 256);
        ROUTE_STEP = builder
                .comment("Size in blocks of a grid cell for the calculation of the routes between the villages.")
                .translation(KEY_PREFIX + "routeStep")
                .defineInRange("routeStep", 32, 4, 64);
        ROUTE_MAX_LENGTH = builder
                .comment("Maximum length of a route in blocks. Two villages with no shorter route do not trade.")
                .translation(KEY_PREFIX + "routeMaxLength")
                .defineInRange("routeMaxLength", 3000, 100, 20000);
        ROUTE_MAX_NODES = builder
                .comment("Maximum number of grid cells that the calculation of one route examines. Each cell reads 2",
                        "heights from the terrain noise on the route worker thread.")
                .translation(KEY_PREFIX + "routeMaxNodes")
                .defineInRange("routeMaxNodes", 1500, 100, 100000);
        ROUTE_BOATS = builder
                .comment("True if the caravans cross deep water in boats. Else a route does not go into deep water.")
                .translation(KEY_PREFIX + "routeBoats")
                .define("routeBoats", true);
        ROUTE_DETAIL_STEP = builder
                .comment("Size in blocks of a grid cell for the detailed route of a group. The group calculates its",
                        "detailed route near the coarse route while it waits at its village.")
                .translation(KEY_PREFIX + "routeDetailStep")
                .defineInRange("routeDetailStep", 4, 2, 16);
        ROUTE_MAX_RISE = builder
                .comment("Maximum height change between two cells of a detailed route, in blocks. A higher step is a",
                        "cliff, and the route goes around it.")
                .translation(KEY_PREFIX + "routeMaxRise")
                .defineInRange("routeMaxRise", 3, 1, 32);
        ROUTE_CORRIDOR = builder
                .comment("Maximum distance in blocks from a detailed route to the coarse route. If no detailed route",
                        "is in this corridor, the calculation tries again with a corridor two times wider.")
                .translation(KEY_PREFIX + "routeCorridor")
                .defineInRange("routeCorridor", 48, 8, 512);
        ROUTE_DETAIL_MAX_NODES = builder
                .comment("Maximum number of grid cells that the calculation of one detailed route examines, for each",
                        "corridor.")
                .translation(KEY_PREFIX + "routeDetailMaxNodes")
                .defineInRange("routeDetailMaxNodes", 60000, 1000, 2000000);
        TRADE_CARAVANS_PER_TICK = builder
                .comment("Maximum number of new caravans of the trade between villages on each abstract tick.")
                .translation(KEY_PREFIX + "tradeCaravansPerTick")
                .defineInRange("tradeCaravansPerTick", 2, 0, 64);
        TRADE_CARAVAN_CAPACITY = builder
                .comment("Maximum number of items in one caravan.")
                .translation(KEY_PREFIX + "tradeCaravanCapacity")
                .defineInRange("tradeCaravanCapacity", 256, 1, 2880);
        TRADE_ROUTES_PER_TICK = builder
                .comment("Maximum number of routes that the trade has on the way to the route worker thread.")
                .translation(KEY_PREFIX + "tradeRoutesPerTick")
                .defineInRange("tradeRoutesPerTick", 2, 0, 64);
        TRADE_RELATION_GAIN = builder
                .comment("Relation gain between two factions when a caravan of one arrives at a village of the other.")
                .translation(KEY_PREFIX + "tradeRelationGain")
                .defineInRange("tradeRelationGain", 1, 0, 100);
        TRAVELING_MERCHANTS = builder
                .comment("Number of traveling merchants of the neutral faction in the world.")
                .translation(KEY_PREFIX + "travelingMerchants")
                .defineInRange("travelingMerchants", 2, 0, 16);
        TRAVELING_STAY_TICKS = builder
                .comment("Abstract ticks that a traveling merchant stays near a village (5 = 10 minutes).")
                .translation(KEY_PREFIX + "travelingStayTicks")
                .defineInRange("travelingStayTicks", 5, 1, 1000);
        builder.pop();

        builder.comment("Quests of the faction villages.")
                .translation(KEY_PREFIX + "quests")
                .push("quests");
        QUEST_OFFER_MINUTES = builder
                .comment("Minutes from the offer of a quest to the end of the offer (20 minutes = 1 game day).")
                .translation(KEY_PREFIX + "questOfferMinutes")
                .defineInRange("offerMinutes", 40, 1, 10000);
        QUEST_DELIVERY_MINUTES = builder
                .comment("Minutes from the acceptance of a delivery quest to its deadline.")
                .translation(KEY_PREFIX + "questDeliveryMinutes")
                .defineInRange("deliveryMinutes", 60, 1, 10000);
        QUEST_DELIVERY_BONUS = builder
                .comment("Factor of the local price of the items for the emerald reward of a delivery quest.")
                .translation(KEY_PREFIX + "questDeliveryBonus")
                .defineInRange("deliveryBonus", 1.5, 0.1, 10.0);
        QUEST_DELIVERY_REPUTATION = builder
                .comment("Reputation reward of a delivery quest.")
                .translation(KEY_PREFIX + "questDeliveryReputation")
                .defineInRange("deliveryReputation", 20, 0, 1000);
        QUEST_DELIVERY_MAX_COUNT = builder
                .comment("Maximum quantity of items of a delivery quest.")
                .translation(KEY_PREFIX + "questDeliveryMaxCount")
                .defineInRange("deliveryMaxCount", 32, 1, 640);
        QUEST_FRIEND_REWARD = builder
                .comment("A quest with this reward in emeralds or more is only for the tiers friend and ally.")
                .translation(KEY_PREFIX + "questFriendReward")
                .defineInRange("friendReward", 20, 1, 100000);
        QUEST_MAX_ACTIVE = builder
                .comment("Maximum number of active quests of a player.")
                .translation(KEY_PREFIX + "questMaxActive")
                .defineInRange("maxActive", 3, 1, 20);
        QUEST_ESCORT_OFFERS = builder
                .comment("Each new trade caravan offers an escort quest and waits at its village.")
                .translation(KEY_PREFIX + "questEscortOffers")
                .define("escortOffers", true);
        QUEST_ESCORT_WAIT_TICKS = builder
                .comment("Abstract ticks that a caravan waits for an escort before it leaves (3 = 6 minutes).")
                .translation(KEY_PREFIX + "questEscortWaitTicks")
                .defineInRange("escortWaitTicks", 3, 1, 100);
        QUEST_ESCORT_DISTANCE = builder
                .comment("Horizontal distance in blocks from the player to the escorted caravan. Farther, the",
                        "caravan stops.")
                .translation(KEY_PREFIX + "questEscortDistance")
                .defineInRange("escortDistance", 48, 8, 256);
        QUEST_ESCORT_AWAY_SECONDS = builder
                .comment("Seconds that the player can stay farther than the escort distance. After that, the quest",
                        "fails and the caravan continues with no escort.")
                .translation(KEY_PREFIX + "questEscortAwaySeconds")
                .defineInRange("escortAwaySeconds", 120, 10, 3600);
        QUEST_ESCORT_MINUTES = builder
                .comment("Minimum minutes from the acceptance of an escort quest to its deadline.")
                .translation(KEY_PREFIX + "questEscortMinutes")
                .defineInRange("escortMinutes", 40, 1, 10000);
        QUEST_ESCORT_BLOCKS_PER_MINUTE = builder
                .comment("Speed of a caravan for the time of an escort, in blocks per minute. The time of an escort is",
                        "the length of the route at this speed, plus 10 minutes, and at least escortMinutes. A",
                        "caravan with entities walks faster: the difference is the margin of the player.")
                .translation(KEY_PREFIX + "questEscortBlocksPerMinute")
                .defineInRange("escortBlocksPerMinute", 60, 5, 10000);
        QUEST_ESCORT_REWARD_SHARE = builder
                .comment("Share of the value of the cargo that the player gets for an escort, in emeralds.")
                .translation(KEY_PREFIX + "questEscortRewardShare")
                .defineInRange("escortRewardShare", 0.5, 0.0, 10.0);
        QUEST_ESCORT_REPUTATION = builder
                .comment("Reputation reward of an escort quest.")
                .translation(KEY_PREFIX + "questEscortReputation")
                .defineInRange("escortReputation", 30, 0, 1000);
        builder.pop();

        builder.comment("Merchants of the faction villages.")
                .translation(KEY_PREFIX + "merchant")
                .push("merchant");
        MERCHANT_FRIEND_FACTOR = builder
                .comment("Factor of the sell prices for a player in the tier friend.")
                .translation(KEY_PREFIX + "merchantFriendFactor")
                .defineInRange("friendFactor", 0.9, 0.1, 1.0);
        MERCHANT_ALLY_FACTOR = builder
                .comment("Factor of the sell prices for a player in the tier ally.")
                .translation(KEY_PREFIX + "merchantAllyFactor")
                .defineInRange("allyFactor", 0.8, 0.1, 1.0);
        builder.pop();

        builder.comment("Bank of the faction villages.")
                .translation(KEY_PREFIX + "bank")
                .push("bank");
        BANK_PILLAGE_VALUE = builder
                .comment("After a pillage of a bank, the village value and the faction value of the player go to this",
                        "value. A lower value does not change. -800 is the tier sworn enemy.")
                .translation(KEY_PREFIX + "bankPillageValue")
                .defineInRange("pillageValue", -800, -1000, 1000);
        BANK_PILLAGE_LOSS = builder
                .comment("Experience that a settlement loses at the first pillage of its bank by a player.")
                .translation(KEY_PREFIX + "bankPillageLoss")
                .defineInRange("pillageLoss", 50, 0, 10000);
        BANK_TREASURY_SHARE = builder
                .comment("Share of the faction treasury in the banks. Each active village of the faction has an",
                        "equal part of this share.")
                .translation(KEY_PREFIX + "bankTreasuryShare")
                .defineInRange("treasuryShare", 0.4, 0.0, 1.0);
        builder.pop();

        builder.comment("Base values of each unit type.")
                .translation(KEY_PREFIX + "unit")
                .push("unit");
        GUARD = new UnitStats(builder, "guard", new UnitStats.Defaults(30.0, 3.0, 4.0, 0.3, 24.0));
        // For the archer, attackDamage is the base damage of its arrows.
        ARCHER = new UnitStats(builder, "archer", new UnitStats.Defaults(20.0, 2.0, 2.0, 0.3, 32.0));
        CAPTAIN = new UnitStats(builder, "captain", new UnitStats.Defaults(40.0, 4.0, 6.0, 0.3, 28.0));
        builder.pop();

        SPEC = builder.build();
    }

    private WorldConfig() {
    }
}
