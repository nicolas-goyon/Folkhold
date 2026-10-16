package io.github.nicolasgoyon.folkhold.world.settlement;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.faction.VillageNames;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.faction.Culture;
import io.github.nicolasgoyon.folkhold.world.faction.Cultures;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.group.CaravanRoutes;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.village.BankChests;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Link between the settlements (abstract state) and the village hearts (concrete state). A new settlement starts
 * its economy at once.
 */
public final class Settlements {
    /** The structure of the faction villages. */
    public static final ResourceKey<Structure> VILLAGE_STRUCTURE = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village"));

    private Settlements() {
    }

    /**
     * Makes a new active settlement. The faction is the faction with the nearest territory center.
     *
     * @param server     the server
     * @param id         the id of the settlement
     * @param startChunk the chunk of the structure start
     * @param pos        the position of the settlement (heart, or approximate position). The Y value selects the
     *                   biome.
     * @return the settlement, not yet in the registry
     */
    public static Settlement create(MinecraftServer server, UUID id, ChunkPos startChunk, BlockPos pos) {
        FactionSpec faction = FactionData.get(server).ownerAt(pos);
        Settlement settlement = new Settlement(id.toString(), startChunk.x, startChunk.z, faction.id(),
                name(faction, id), pos.getX(), pos.getZ());
        settlement.setProgress(startLevel(settlement.id()), 0);
        settlement.setBiome(Economies.biomeAt(server.overworld(), pos.getX(), pos.getY(), pos.getZ()));
        startEconomy(server, settlement);
        return settlement;
    }

    /**
     * Starts the economy of a settlement (population, jobs, start stock) if it did not start and if the data packs
     * give jobs.
     *
     * @param server     the server
     * @param settlement the settlement
     */
    public static void startEconomy(MinecraftServer server, Settlement settlement) {
        if (EconomyData.isActive() && !settlement.economy().isInitialized()) {
            Economies.initialize(server, settlement);
        }
    }

    /**
     * The start level of a settlement, from the level table. The same id gives the same level.
     *
     * @param id the id of the settlement
     * @return the start level
     */
    public static int startLevel(String id) {
        long seed;
        try {
            UUID uuid = UUID.fromString(id);
            seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        } catch (IllegalArgumentException e) {
            seed = id.hashCode();
        }
        return LevelTables.get().startLevel(seed);
    }

    /**
     * The settlement of a village heart, if the heart is linked.
     *
     * @param server the server
     * @param heart  the heart
     * @return the settlement
     */
    public static Optional<Settlement> of(MinecraftServer server, VillageHeartBlockEntity heart) {
        return SettlementData.get(server).registry().byId(heart.getVillageId().toString());
    }

    /**
     * The name of a village of a faction. The same id gives the same name.
     *
     * @param faction the faction
     * @param id      the id of the village
     * @return the name
     */
    public static String name(FactionSpec faction, UUID id) {
        return Cultures.byId(faction.cultureId())
                .map(Culture::info)
                .map(culture -> VillageNames.name(culture, id.getMostSignificantBits() ^ id.getLeastSignificantBits()))
                .orElse(faction.name());
    }

    /**
     * The start chunk of the village structure at a position. A position outside a village structure (for example
     * a heart that a player placed) gives the chunk of the position.
     *
     * @param level the level
     * @param pos   the position
     * @return the start chunk
     */
    public static ChunkPos startChunk(ServerLevel level, BlockPos pos) {
        Optional<Structure> structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getOptional(VILLAGE_STRUCTURE);
        if (structure.isPresent()) {
            StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structure.get());
            if (start.isValid()) {
                return start.getChunkPos();
            }
        }
        return new ChunkPos(pos);
    }

    /**
     * Links a village heart to its settlement. The heart and the settlement get the same id, faction and name.
     *
     * <ul>
     *   <li>A settlement with the id of the heart exists: the settlement gets the position of the heart.</li>
     *   <li>The discovery found the settlement before the heart loaded, and the heart has no faction: the heart
     *       gets the id, the faction and the name of the settlement.</li>
     *   <li>The discovery found the settlement, and the heart has a faction: the heart is older than the
     *       settlement. The settlement gets the id, the faction and the name of the heart, because the
     *       reputations use the id of the heart.</li>
     *   <li>No settlement: the mod makes a settlement with the id of the heart.</li>
     * </ul>
     *
     * @param level the level of the heart
     * @param heart the heart
     * @return the settlement of the heart
     */
    public static Settlement bind(ServerLevel level, VillageHeartBlockEntity heart) {
        MinecraftServer server = level.getServer();
        SettlementData data = SettlementData.get(server);
        BlockPos pos = heart.getBlockPos();
        String heartId = heart.getVillageId().toString();
        Settlement settlement = data.registry().byId(heartId).orElse(null);
        if (settlement == null) {
            ChunkPos start = startChunk(level, pos);
            Settlement discovered = data.registry().byStart(start.x, start.z).stream()
                    .filter(found -> !found.hasHeart())
                    .findFirst()
                    .orElse(null);
            if (discovered != null && heart.getFactionId() == null) {
                settlement = discovered;
            } else if (discovered != null) {
                changeId(server, discovered, heartId);
                discovered.setFactionId(heart.getFactionId());
                settlement = discovered;
                String heartName = heart.getVillageName();
                if (heartName != null) {
                    settlement.setName(heartName);
                }
            } else {
                settlement = create(server, heart.getVillageId(), start, pos);
                if (heart.getFactionId() != null) {
                    settlement.setFactionId(heart.getFactionId());
                    String heartName = heart.getVillageName();
                    if (heartName != null) {
                        settlement.setName(heartName);
                    }
                }
                data.registry().add(settlement);
            }
        }
        settlement.setHeart(pos.getX(), pos.getY(), pos.getZ());
        startEconomy(server, settlement);
        heart.adoptSettlement(UUID.fromString(settlement.id()), settlement.factionId(), settlement.name());
        data.setDirty();
        return settlement;
    }

    /** Players in this radius around a destroyed village get a message. */
    private static final double DESTRUCTION_MESSAGE_RADIUS = 128.0;

    /**
     * Destroys a settlement: the settlement goes to the state {@link SettlementState#RUINS}. Its territory cells
     * become unclaimed, and the simulation ignores it. The blocks of the village stay in the world.
     *
     * <ul>
     *   <li>The stock and the share of the treasury go into the bank chests as real items
     *       ({@link BankChests#storeInRuins}).</li>
     *   <li>The players near the village get a message.</li>
     *   <li>The player who caused the destruction loses reputation with the faction
     *       ({@link WorldConfig#REPUTATION_DESTROY_VILLAGE}).</li>
     * </ul>
     *
     * @param level      the level of the village
     * @param settlementId the id of the settlement
     * @param pos        the position of the village heart
     * @param cause      the player who caused the destruction, or null
     */
    public static void ruin(ServerLevel level, String settlementId, BlockPos pos, @Nullable ServerPlayer cause) {
        MinecraftServer server = level.getServer();
        SettlementData data = SettlementData.get(server);
        Optional<Settlement> found = data.registry().byId(settlementId).filter(Settlement::isActive);
        if (found.isEmpty()) {
            return;
        }
        Settlement settlement = found.get();
        BankChests.storeInRuins(level, settlement, heartPos(settlement, pos));
        settlement.setState(SettlementState.RUINS);
        data.setDirty();
        FolkholdWorld.LOGGER.info("The settlement {} is destroyed", settlement);
        Component factionName = FactionData.get(server).byId(settlement.factionId())
                .<Component>map(FactionVisuals::name)
                .orElse(Component.literal(settlement.factionId()));
        Component message = Component.translatable("message.folkhold_world.village_destroyed", settlement.name(),
                factionName);
        double radiusSquared = DESTRUCTION_MESSAGE_RADIUS * DESTRUCTION_MESSAGE_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getCenter()) <= radiusSquared) {
                player.sendSystemMessage(message);
            }
        }
        if (cause != null) {
            Reputations.apply(server, cause, settlement.factionId(), settlement.id(),
                    WorldConfig.REPUTATION_DESTROY_VILLAGE.get());
        }
    }

    /**
     * Changes the id of a settlement everywhere: in the registry, in the mobile groups and their routes, and in
     * the quests. The routes on the way with the old id are dropped.
     *
     * @param server     the server
     * @param settlement the settlement
     * @param newId      the new id
     */
    public static void changeId(MinecraftServer server, Settlement settlement, String newId) {
        String oldId = settlement.id();
        if (oldId.equals(newId)) {
            return;
        }
        SettlementData.get(server).registry().changeId(oldId, newId);
        CaravanRoutes.forgetPending(server, oldId);
        MobileGroupData.get(server).renameSettlement(oldId, newId);
        QuestData.get(server).renameSettlement(oldId, newId);
        SettlementData.get(server).setDirty();
    }

    /**
     * Makes a settlement in ruins active again:
     * <ul>
     *   <li>the settlement goes to the state {@link SettlementState#ACTIVE};</li>
     *   <li>if its heart is not at its position, a new heart comes there, linked to the same settlement;</li>
     *   <li>the items of the bank chests go back into the stock and the treasury
     *       ({@link BankChests#recoverFromRuins}).</li>
     * </ul>
     * The garrison and the villagers come back when a player is near the heart, as in each village. The chunk of
     * the heart loads first when it is not loaded.
     *
     * @param level      the level of the village
     * @param settlement the settlement
     * @return the position of the heart
     */
    public static BlockPos restore(ServerLevel level, Settlement settlement) {
        BlockPos center = new BlockPos(settlement.x(), 0, settlement.z());
        if (!level.isLoaded(center)) {
            level.getChunk(center);
        }
        BlockPos surface = new BlockPos(settlement.x(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                settlement.x(), settlement.z()), settlement.z());
        BlockPos heartPos = heartPos(settlement, surface);
        if (!level.getBlockState(heartPos).is(ModBlocks.VILLAGE_HEART.get())) {
            level.setBlock(heartPos, ModBlocks.VILLAGE_HEART.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        settlement.setState(SettlementState.ACTIVE);
        if (level.getBlockEntity(heartPos) instanceof VillageHeartBlockEntity heart) {
            heart.adoptSettlement(UUID.fromString(settlement.id()), settlement.factionId(), settlement.name());
            heart.ensureSettlement();
            VillageHearts.register(level, heartPos);
        }
        BankChests.recoverFromRuins(level, settlement, heartPos);
        SettlementData.get(level.getServer()).setDirty();
        FolkholdWorld.LOGGER.info("The settlement {} is active again", settlement);
        return heartPos;
    }

    /** The position of the heart of a settlement. A settlement with no known heart uses the given position. */
    private static BlockPos heartPos(Settlement settlement, BlockPos fallback) {
        return settlement.hasHeart() ? new BlockPos(settlement.x(), settlement.heartY(), settlement.z()) : fallback;
    }
}
