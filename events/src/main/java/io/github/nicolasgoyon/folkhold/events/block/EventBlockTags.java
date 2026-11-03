package io.github.nicolasgoyon.folkhold.events.block;

import io.github.nicolasgoyon.folkhold.events.FolkholdEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block tags of the natural events. All the events (earthquakes, storms) use them.
 *
 * <ul>
 *   <li>{@code folkhold_events:fragile}, {@code folkhold_events:sturdy}, {@code folkhold_events:resistant}: the class
 *       of a block (see {@link BlockClass}).</li>
 *   <li>{@code folkhold_events:shatters}: a block that breaks with no drop, in place of a fall. Examples: glass, ice
 *       and leaves.</li>
 *   <li>{@code folkhold_events:crack_surface}: the top block of a column where a crack can open: natural surface,
 *       for example grass, dirt, sand and gravel.</li>
 *   <li>{@code folkhold_events:crackable}: natural ground that a crack can remove below the surface block. Examples:
 *       dirt, sand, gravel and stone.</li>
 *   <li>{@code folkhold_events:protected}: a block that no event moves or breaks, in addition to the blocks with a
 *       block entity and the blocks that a player cannot break. Examples: reinforced deepslate, respawn anchor,
 *       lodestone and the important blocks of the Folkhold villages.</li>
 * </ul>
 */
public final class EventBlockTags {
    public static final TagKey<Block> FRAGILE = tag("fragile");
    public static final TagKey<Block> STURDY = tag("sturdy");
    public static final TagKey<Block> RESISTANT = tag("resistant");
    public static final TagKey<Block> SHATTERS = tag("shatters");
    public static final TagKey<Block> CRACK_SURFACE = tag("crack_surface");
    public static final TagKey<Block> CRACKABLE = tag("crackable");
    public static final TagKey<Block> PROTECTED = tag("protected");

    private EventBlockTags() {
    }

    /**
     * True if no event moves or breaks a block: a block of the tag {@code folkhold_events:protected}, a block that a
     * player cannot break (bedrock, barrier), or a block with a block entity (chests, spawners, beds, signs). The
     * block entity keeps data that a fall can lose.
     *
     * @param level the level
     * @param pos   the position of the block
     * @param state the block state
     * @return true for a protected block
     */
    public static boolean isProtected(BlockGetter level, BlockPos pos, BlockState state) {
        return state.is(PROTECTED) || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F;
    }

    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(FolkholdEvents.MOD_ID, name));
    }
}
