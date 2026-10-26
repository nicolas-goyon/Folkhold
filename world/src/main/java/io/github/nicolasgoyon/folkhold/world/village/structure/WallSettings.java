package io.github.nicolasgoyon.folkhold.world.village.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nicolasgoyon.folkhold.core.sim.village.WallLayout;
import net.minecraft.resources.ResourceLocation;

/**
 * The settings of the wall of a fortified village (see {@link FortifiedVillageStructure}).
 *
 * @param margin       the free blocks between the pieces of the village and the palisade
 * @param towerSpacing the maximum distance between the middles of 2 towers of a side (see {@link WallLayout})
 * @param tower        the structure template of a tower with its stair. The outside of the template is to the west and
 *                     to the north. The stair goes along the north side to the east.
 * @param gate         the structure template of a gate. The outside of the template is to the north: the line of the
 *                     palisade is the second row.
 * @param palisade     the blocks of the palisade
 */
public record WallSettings(int margin, int towerSpacing, ResourceLocation tower, ResourceLocation gate,
        PalisadeBlocks palisade) {
    /** The largest margin. */
    public static final int MAX_MARGIN = 16;
    /** The largest tower spacing. */
    public static final int MAX_TOWER_SPACING = 128;

    public static final Codec<WallSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, MAX_MARGIN).fieldOf("margin").forGetter(WallSettings::margin),
            Codec.intRange(WallLayout.MIN_TOWER_SPACING, MAX_TOWER_SPACING).fieldOf("tower_spacing")
                    .forGetter(WallSettings::towerSpacing),
            ResourceLocation.CODEC.fieldOf("tower").forGetter(WallSettings::tower),
            ResourceLocation.CODEC.fieldOf("gate").forGetter(WallSettings::gate),
            PalisadeBlocks.CODEC.fieldOf("palisade").forGetter(WallSettings::palisade))
            .apply(instance, WallSettings::new));
}
