package io.github.nicolasgoyon.folkhold.events.block;

import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The class of a block for the natural events. A fragile block takes much damage, a resistant block takes little
 * damage.
 *
 * <p>The tags of {@link EventBlockTags} give the class. When a block is in more than one class tag, the strongest
 * class applies. A block with no class tag gets its class from its blast resistance (see
 * {@link EventsConfig#FRAGILE_BELOW} and {@link EventsConfig#RESISTANT_FROM}).
 */
public enum BlockClass {
    /** Dirt, sand, gravel, wood, glass, wool and leaves. */
    FRAGILE,
    /** Cobblestone, terracotta and mud bricks. */
    STURDY,
    /** Stone, bricks, deepslate, concrete and obsidian. */
    RESISTANT;

    /**
     * The class of a block state.
     *
     * @param state the block state
     * @return the class from the tags, else from the blast resistance
     */
    // The location sensitive resistance of NeoForge needs an explosion. The class of a block exists with no explosion,
    // so the method reads the resistance of the block.
    @SuppressWarnings("deprecation")
    public static BlockClass of(BlockState state) {
        if (state.is(EventBlockTags.RESISTANT)) {
            return RESISTANT;
        }
        if (state.is(EventBlockTags.STURDY)) {
            return STURDY;
        }
        if (state.is(EventBlockTags.FRAGILE)) {
            return FRAGILE;
        }
        return ofBlastResistance(state.getBlock().getExplosionResistance(), EventsConfig.FRAGILE_BELOW.get(),
                EventsConfig.RESISTANT_FROM.get());
    }

    /**
     * The class of a block with no class tag.
     *
     * @param blastResistance the blast resistance of the block
     * @param fragileBelow    a resistance below this value is fragile
     * @param resistantFrom   a resistance at this value or more is resistant
     * @return the class
     */
    public static BlockClass ofBlastResistance(double blastResistance, double fragileBelow, double resistantFrom) {
        if (blastResistance >= resistantFrom) {
            return RESISTANT;
        }
        return blastResistance < fragileBelow ? FRAGILE : STURDY;
    }
}
