package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignBlock;
import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignKind;
import io.github.nicolasgoyon.folkhold.blocks.block.JobSignBlockEntity;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The signs and the home beds of a structure template, as the scan of the heart reads them (see {@link VillageLayout}
 * and {@link VillageBeds}): the building signs of each kind, the jobs of the job signs and the beds. The world
 * generation can use this class: it reads no level.
 *
 * <p>A market sign counts only with a market post behind it ({@link VillageLayout#MARKET_POSTS}): the sign of the
 * fish market of a maritime village does not count. The beds of a garrison building (a template name that ends with
 * {@link VillageBeds#GARRISON_SUFFIX}) are not home beds: they give no place.
 *
 * @param kinds the number of building signs of each kind, for example {@code storehouse}
 * @param jobs  the job ids of the job signs, for example {@code folkhold_world:fisher}
 * @param beds  the number of home beds: the head part of each bed, 0 in a garrison building
 */
public record TemplateSigns(Map<String, Integer> kinds, List<String> jobs, int beds) {
    /** The signs of a template that the game does not find. */
    public static final TemplateSigns NONE = new TemplateSigns(Map.of(), List.of(), 0);

    public TemplateSigns {
        kinds = Map.copyOf(kinds);
        jobs = List.copyOf(jobs);
    }

    /**
     * Reads the signs and the home beds of a template.
     *
     * @param id       the id of the template
     * @param template the template
     * @return the signs
     */
    public static TemplateSigns of(ResourceLocation id, StructureTemplate template) {
        StructurePlaceSettings settings = new StructurePlaceSettings();
        Set<BlockPos> posts = new HashSet<>();
        for (Holder<Block> post : BuiltInRegistries.BLOCK.getTagOrEmpty(VillageLayout.MARKET_POSTS)) {
            template.filterBlocks(BlockPos.ZERO, settings, post.value()).forEach(info -> posts.add(info.pos()));
        }
        Map<String, Integer> kinds = new HashMap<>();
        for (Map.Entry<BuildingSignKind, DeferredBlock<BuildingSignBlock>> sign : ModBlocks.BUILDING_SIGNS.entrySet()) {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings,
                    sign.getValue().get())) {
                BlockPos back = info.pos().relative(info.state().getValue(HorizontalDirectionalBlock.FACING)
                        .getOpposite());
                if (sign.getKey() != BuildingSignKind.MARKET || posts.contains(back)) {
                    kinds.merge(sign.getKey().serializedName(), 1, Integer::sum);
                }
            }
        }
        List<String> jobs = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings,
                ModBlocks.JOB_SIGN.get())) {
            jobs.add(JobPosts.jobId(info.nbt() == null ? "" : info.nbt().getString(JobSignBlockEntity.JOB),
                    FolkholdWorld.MOD_ID));
        }
        int beds = 0;
        if (!id.getPath().endsWith(VillageBeds.GARRISON_SUFFIX)) {
            for (Holder<Block> bed : BuiltInRegistries.BLOCK.getTagOrEmpty(BlockTags.BEDS)) {
                if (bed.value() instanceof BedBlock block) {
                    beds += (int) template.filterBlocks(BlockPos.ZERO, settings, block).stream()
                            .filter(info -> info.state().getValue(BedBlock.PART) == BedPart.HEAD).count();
                }
            }
        }
        return new TemplateSigns(kinds, jobs, beds);
    }
}
