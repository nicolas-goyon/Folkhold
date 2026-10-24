package io.github.nicolasgoyon.folkhold.core.sim.construction;

/** The blocks of the level before the construction. */
@FunctionalInterface
public interface LevelView {
    /**
     * The block at a position of the level.
     *
     * @param pos the position
     * @return the block, or {@link BuildBlock#AIR} for an empty position
     */
    BuildBlock blockAt(BuildPos pos);
}
