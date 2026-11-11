package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the templates of the homes of the villages: the pieces of the pools {@code homes} and
 * {@code houses} of each style and each biome variant, written by {@code tools/structures/village.py}. The plan test
 * of these pieces is {@link VillageTemplateGameTests#eachVillagePlanHasNoWarning}.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HomeTemplateGameTests {
    /** The pools of the homes in the folder of a style and a variant. */
    private static final List<String> HOME_POOLS = List.of("homes", "houses");
    /**
     * The number of home pieces: 4 homes of all the cultures, 6 Valeholm homes, 5 Sunreach homes and 6 maritime
     * homes, in each of the 6 variants.
     */
    static final int HOME_PIECES = (4 + 6 + 5 + 6) * 6;

    private HomeTemplateGameTests() {
    }

    /**
     * In each home piece of the pools of each style and each variant, a villager can go from the front of the piece to
     * each work block and to each bed: a walk from the front reaches a position next to the block (see
     * {@link TemplateReach#unreachable}). The test checks exactly {@link #HOME_PIECES} pieces: a missing template
     * fails it.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "home_templates")
    public static void eachHomeWorkBlockAndBedCanBeReached(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        Set<ResourceLocation> pieces = homePieces(level, problems);
        for (ResourceLocation id : pieces) {
            Optional<StructureTemplate> template = level.getStructureManager().get(id);
            if (template.isEmpty()) {
                problems.add("no template " + id);
                continue;
            }
            List<String> blocks = TemplateReach.unreachable(level, template.get());
            if (!blocks.isEmpty()) {
                problems.add(id + ": " + blocks);
            }
        }
        helper.assertTrue(pieces.size() == HOME_PIECES, "Expected " + HOME_PIECES + " home pieces to check: "
                + pieces.size());
        helper.assertTrue(problems.isEmpty(), "Work blocks and beds that a villager cannot reach from the front: "
                + problems);
        helper.succeed();
    }

    /**
     * The templates of the pieces of the pools {@link #HOME_POOLS} of each variant of the village structure (see
     * {@link VillagePieces}).
     */
    private static Set<ResourceLocation> homePieces(ServerLevel level, List<String> problems) {
        Set<ResourceLocation> pieces = new TreeSet<>();
        VillagePieces.all(level, problems).values().stream()
                .filter(piece -> HOME_POOLS.stream().anyMatch(piece::inPool))
                .forEach(piece -> pieces.add(piece.template()));
        return pieces;
    }
}
