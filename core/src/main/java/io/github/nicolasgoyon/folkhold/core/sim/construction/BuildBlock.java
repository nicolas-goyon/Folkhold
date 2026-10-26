package io.github.nicolasgoyon.folkhold.core.sim.construction;

import java.util.Objects;
import java.util.Optional;

/**
 * A block of a target, or of the level.
 *
 * @param state           the block state as text, with all its properties (for example
 *                        {@code minecraft:oak_door[facing=north,half=lower,...]})
 * @param kind            the kind of the block
 * @param support         the side of the support block. For a falling block: below. For an attached block: the
 *                        side of its support. For a two-part block that needs a floor: the side of the floor. Empty
 *                        for the other blocks.
 * @param supportRequired true if the block breaks with no support, false for a block with an optional support
 * @param partner         the position of the other part, for a block of two parts
 * @param primary         true for most blocks. False for the part of a two-part block that the step of the other part
 *                        places. Examples: the upper half of a door, the head of a bed.
 * @param dryState        the state with no fluid, for a block with a fluid in it (a waterlogged block). The
 *                        construction places this state, and adds the fluid at the end.
 * @param data            the data of the block entity that comes with the block, for example the text of a sign.
 *                        Empty for the other blocks.
 * @param floor           true if a builder can stand on the block
 * @param passable        true if a builder can stand in the block: the block has no collision
 * @param walk            how a builder walks through the block or on it, on its way to a position to stand
 */
public record BuildBlock(String state, BlockKind kind, Optional<Side> support, boolean supportRequired,
        Optional<BuildPos> partner, boolean primary, Optional<String> dryState, Optional<String> data, boolean floor,
        boolean passable, Walk walk) {
    /** The state of an empty position. */
    public static final String AIR_STATE = "minecraft:air";
    /** The state of the scaffolding that the plan adds. */
    public static final String SCAFFOLDING_STATE = "minecraft:scaffolding";
    /** An empty position. */
    public static final BuildBlock AIR = new BuildBlock(AIR_STATE, BlockKind.AIR, Optional.empty(), false,
            Optional.empty(), true, Optional.empty(), Optional.empty(), false, true, Walk.THROUGH);
    /** The scaffolding of the plan: a builder stands on it. */
    public static final BuildBlock SCAFFOLDING = new BuildBlock(SCAFFOLDING_STATE, BlockKind.SOLID, Optional.empty(),
            false, Optional.empty(), true, Optional.empty(), Optional.empty(), true, false, Walk.ON);

    public BuildBlock {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(partner, "partner");
        Objects.requireNonNull(dryState, "dryState");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(walk, "walk");
    }

    /** A block that stays in place alone. A builder walks on it. */
    public static BuildBlock solid(String state) {
        return new BuildBlock(state, BlockKind.SOLID, Optional.empty(), false, Optional.empty(), true, Optional.empty(),
                Optional.empty(), true, false, Walk.ON);
    }

    /** A block that falls with no block below it. A builder walks on it. */
    public static BuildBlock falling(String state) {
        return new BuildBlock(state, BlockKind.FALLING, Optional.of(Side.DOWN), true, Optional.empty(), true,
                Optional.empty(), Optional.empty(), true, false, Walk.ON);
    }

    /** A block with no collision that needs a support on a side (a torch, a ladder, a sign). */
    public static BuildBlock attached(String state, Side support) {
        return new BuildBlock(state, BlockKind.ATTACHED, Optional.of(support), true, Optional.empty(), true,
                Optional.empty(), Optional.empty(), false, true, Walk.THROUGH);
    }

    /**
     * A block with an optional support: it stays in place alone, and it comes after the block on the side. A builder
     * walks on it.
     */
    public static BuildBlock optionalSupport(String state, Side support) {
        return new BuildBlock(state, BlockKind.ATTACHED, Optional.of(support), false, Optional.empty(), true,
                Optional.empty(), Optional.empty(), true, false, Walk.ON);
    }

    /**
     * A block with a shape from its neighbors. It stops a builder, as a fence does. For stairs, see
     * {@link #withWalk}.
     */
    public static BuildBlock connected(String state) {
        return new BuildBlock(state, BlockKind.CONNECTED, Optional.empty(), false, Optional.empty(), true,
                Optional.empty(), Optional.empty(), true, false, Walk.BLOCKED);
    }

    /** A source of a fluid. A builder walks through it, as through water. */
    public static BuildBlock fluid(String state) {
        return new BuildBlock(state, BlockKind.FLUID, Optional.empty(), false, Optional.empty(), true, Optional.empty(),
                Optional.empty(), false, true, Walk.THROUGH);
    }

    /**
     * One part of a block of two parts. A builder walks on it, as on a bed. For a door, see {@link #withWalk}.
     *
     * @param state   the state of this part
     * @param partner the position of the other part
     * @param primary true for the part that the step places with the other part
     * @param floor   the side of the floor that the part needs, or empty
     */
    public static BuildBlock twoPart(String state, BuildPos partner, boolean primary, Optional<Side> floor) {
        return new BuildBlock(state, BlockKind.TWO_PART, floor, floor.isPresent(), Optional.of(partner), primary,
                Optional.empty(), Optional.empty(), false, false, Walk.ON);
    }

    /** The same block with a fluid in it: the construction places the dry state first. */
    public BuildBlock withFluid(String dry) {
        return new BuildBlock(state, kind, support, supportRequired, partner, primary, Optional.of(dry), data, floor,
                passable, walk);
    }

    /** The same block with the data of its block entity. */
    public BuildBlock withData(String blockData) {
        return new BuildBlock(state, kind, support, supportRequired, partner, primary, dryState,
                Optional.of(blockData), floor, passable, walk);
    }

    /** The same block with another way to walk through it or on it. */
    public BuildBlock withWalk(Walk newWalk) {
        return new BuildBlock(state, kind, support, supportRequired, partner, primary, dryState, data, floor, passable,
                newWalk);
    }

    /** True if the two blocks have the same state and the same data. The construction does not change such a block. */
    public boolean sameBlock(BuildBlock other) {
        return state.equals(other.state) && data.equals(other.data);
    }
}
