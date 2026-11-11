package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.village.TemplateSigns;
import io.github.nicolasgoyon.folkhold.world.village.VillageBeds;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for {@link TemplateSigns}: the generation reads the signs and the beds of a template as the scan of the
 * heart reads them in the world (see {@link VillageLayout}). Thus the counts of the generation and of the scan stay the
 * same.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TemplateSignsGameTests {
    /** The pools of the maritime variant: the houses (fishing hut, fish market, smokehouse), the market, the jobs. */
    private static final List<String> POOLS = List.of("village/maritime/houses", "village/maritime/market",
            "village/maritime/jobs");
    /** The position of each placed template, in the test structure. */
    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);

    private TemplateSignsGameTests() {
    }

    /**
     * Each template of the pools {@code houses}, {@code market} and {@code jobs} of the maritime variant: the scan of its
     * box in the world finds the building signs of {@link TemplateSigns#kinds()}, the jobs of
     * {@link TemplateSigns#jobs()} and, out of a garrison building, the beds of {@link TemplateSigns#beds()}.
     */
    @GameTest(template = "arena_large", timeoutTicks = 40, batch = "template_signs", skyAccess = true)
    public static void signsOfTheTemplatesAreTheSignsOfTheScan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String pool : POOLS) {
            StructureTemplatePool templates = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
                    .getOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL,
                            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, pool)));
            for (StructurePoolElement element : templates.getShuffledTemplates(RandomSource.create(0L))) {
                VillageReference.templateOf(level, element).ifPresent(ids::add);
            }
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(JigsawReplacementProcessor.INSTANCE).setKnownShape(true);
        BlockPos origin = helper.absolutePos(ORIGIN);
        List<String> problems = new ArrayList<>();
        for (ResourceLocation id : ids) {
            StructureTemplate template = level.getStructureManager().get(id).orElseThrow();
            BoundingBox box = template.getBoundingBox(settings, origin);
            try {
                template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
                VillageLayout layout = new VillageLayout();
                helper.assertTrue(layout.scan(level, box), "The box of " + id + " must be ready");
                TemplateSigns signs = TemplateSigns.of(id, template);
                List<String> jobs = layout.jobSigns().stream().map(VillageLayout.JobSign::jobId).toList();
                int beds = id.getPath().endsWith(VillageBeds.GARRISON_SUFFIX) ? 0 : layout.beds().size();
                if (!signs.kinds().equals(layout.buildings()) || !signs.jobs().equals(jobs) || signs.beds() != beds) {
                    problems.add(id + ": template " + signs + ", scan " + layout.buildings() + " " + jobs + " "
                            + layout.beds().size() + " beds");
                }
            } finally {
                BlockPos.betweenClosedStream(box).forEach(pos ->
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
            }
        }
        helper.assertTrue(ids.size() > 30, "The pools must have templates: " + ids);
        helper.assertTrue(problems.isEmpty(), "The signs of the templates must be the signs of the scan: " + problems);
        helper.succeed();
    }
}
