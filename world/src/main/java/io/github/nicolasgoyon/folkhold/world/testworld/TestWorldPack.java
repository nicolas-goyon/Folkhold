package io.github.nicolasgoyon.folkhold.world.testworld;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.settlement.FixedChunkPlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

/**
 * The optional built-in data pack "Folkhold test world", in the folder {@code datapacks/test_world} of the jar.
 *
 * <p>The data pack is off by default. A player turns it on in the screen "Data Packs" of the world creation. Then
 * the world type "Folkhold test" shows: a flat world with one faction village next to the spawn point. The
 * data pack contains:
 * <ul>
 *   <li>the world preset {@code folkhold_world:test}, in the tag {@code minecraft:normal} of the world types.</li>
 *   <li>a structure set {@code folkhold_world:villages} with one start chunk ({@link FixedChunkPlacement}), in
 *       place of the structure set of the mod. A world with the data pack and another world type also gets one
 *       faction village only.</li>
 * </ul>
 *
 * <p>The GameTest server turns on all the available packs. The mod does not offer this data pack to the GameTest
 * server, because the data pack changes the placement of the faction villages in all the tests.
 */
public final class TestWorldPack {
    /** The location of the data pack: the namespace is the mod, and the path is the folder in the jar. */
    public static final ResourceLocation LOCATION =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "datapacks/test_world");
    /** The id of the data pack, for example for {@code initial-enabled-packs} of a dedicated server. */
    public static final String PACK_ID = "mod/" + LOCATION;

    /**
     * A built-in source that the game does not turn on by itself. The vanilla source of the built-in packs turns on
     * a new pack in all the worlds.
     */
    private static final PackSource SOURCE = PackSource.create(name -> Component.translatable("pack.nameAndSource",
            name, Component.translatable("pack.source.builtin")).withStyle(ChatFormatting.GRAY), false);

    private TestWorldPack() {
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (GameTestHooks.isGametestServer()) {
            return;
        }
        event.addPackFinders(LOCATION, PackType.SERVER_DATA, Component.translatable("pack.folkhold_world.test_world"),
                SOURCE, false, Pack.Position.TOP);
    }
}
