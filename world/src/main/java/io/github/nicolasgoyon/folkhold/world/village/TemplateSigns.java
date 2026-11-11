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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The signs of a structure template, as the scan of the heart reads them (see {@link VillageLayout}): the building
 * signs of each kind and the jobs of the job signs. The world generation can use this class: it reads no level.
 *
 * <p>A market sign counts only with a market post behind it ({@link VillageLayout#MARKET_POSTS}): the sign of the
 * fish market of a maritime village does not count.
 *
 * @param kinds the number of building signs of each kind, for example {@code storehouse}
 * @param jobs  the job ids of the job signs, for example {@code folkhold_world:fisher}
 */
public record TemplateSigns(Map<String, Integer> kinds, List<String> jobs) {
    public TemplateSigns {
        kinds = Map.copyOf(kinds);
        jobs = List.copyOf(jobs);
    }

    /**
     * Reads the signs of a template.
     *
     * @param template the template
     * @return the signs
     */
    public static TemplateSigns of(StructureTemplate template) {
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
        return new TemplateSigns(kinds, jobs);
    }
}
