package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.cartofold.api.PaperMapMarkers;
import io.github.nicolasgoyon.cartofold.api.PaperMarkerSource;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModMapDecorations;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * The marker of a village on the survey maps of Cartofold.
 *
 * <ul>
 *   <li>A player uses a survey map on a village heart: the map gets a marker at the heart, with the icon
 *       {@link ModMapDecorations#VILLAGE} and the name of the village. A second use removes the marker. Cartofold
 *       keeps the markers in the data of the map.</li>
 *   <li>The heart of a ruin, or a heart with no settlement, gives no marker. A map loses the marker of a village that
 *       becomes a ruin.</li>
 * </ul>
 */
public final class VillageMapMarkers {
    /** The id of the source of the markers. */
    public static final ResourceLocation SOURCE =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village_heart");
    /** The id of the survey map item of Cartofold. */
    static final ResourceLocation SURVEY_MAP = ResourceLocation.fromNamespaceAndPath("cartofold", "survey_map");
    /** The file of the waypoints on the vanilla maps, in the {@code data} folder of the world. */
    static final String OBSOLETE_FILE = "folkhold_map_waypoints.dat";

    private VillageMapMarkers() {
    }

    /** Registers the village heart as a source of markers of the survey maps. */
    public static void register() {
        PaperMapMarkers.register(PaperMarkerSource.of(SOURCE,
                state -> state.is(ModBlocks.VILLAGE_HEART.get()), ModMapDecorations.VILLAGE, VillageMapMarkers::name));
    }

    /**
     * The name of the marker of a village heart.
     *
     * @param level the level
     * @param pos   the position of the heart
     * @return the name of the active settlement of the heart, or empty for a ruin or a heart with no settlement
     */
    static Optional<Component> name(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart)) {
            return Optional.empty();
        }
        return Settlements.of(level.getServer(), heart).filter(Settlement::isActive)
                .map(settlement -> Component.literal(settlement.name()));
    }

    /**
     * Tells if an item is a survey map of Cartofold.
     *
     * <p>Cartofold has no API for this check (Cartofold v0.23.4), thus Folkhold compares the id of the item.
     *
     * @param stack the item
     * @return true for a survey map
     */
    public static boolean isSurveyMap(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(SURVEY_MAP);
    }

    /**
     * Deletes the file of the waypoints on the vanilla maps. Folkhold does not read this file: the players mark the
     * villages again with a survey map.
     *
     * @param event the event
     */
    public static void onServerStarted(ServerStartedEvent event) {
        Path file = event.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(OBSOLETE_FILE);
        try {
            if (Files.deleteIfExists(file)) {
                FolkholdWorld.LOGGER.info("Deleted {}: the survey maps of Cartofold show the villages", file);
            }
        } catch (IOException e) {
            FolkholdWorld.LOGGER.warn("Cannot delete {}", file, e);
        }
    }
}
