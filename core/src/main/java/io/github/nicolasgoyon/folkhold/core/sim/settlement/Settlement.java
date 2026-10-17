package io.github.nicolasgoyon.folkhold.core.sim.settlement;

import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomyState;
import java.util.Objects;

/**
 * Abstract state of one village. The simulation uses it also when the chunks of the village are not loaded.
 *
 * <p>The village heart keeps the concrete state (garrison, alert, layout). The settlement keeps the abstract
 * state. The two have the same id.
 *
 * <p>The start chunk is the chunk where the village structure starts. It identifies the village before the
 * game generates its chunks.
 */
public final class Settlement {
    private String id;
    private final int startChunkX;
    private final int startChunkZ;
    private String factionId;
    private String name;
    private int x;
    private int z;
    private boolean hasHeart;
    private int heartY;
    private SettlementState state = SettlementState.ACTIVE;
    private int level = 1;
    private int experience;
    private String biome = "";
    private final EconomyState economy = new EconomyState();

    /**
     * Makes an active settlement with no known heart.
     *
     * @param id          the id of the settlement (a UUID as text)
     * @param startChunkX chunk X of the structure start
     * @param startChunkZ chunk Z of the structure start
     * @param factionId   the id of the faction that owns the settlement
     * @param name        the name of the settlement
     * @param x           block X of the settlement (approximate before the heart is known)
     * @param z           block Z of the settlement (approximate before the heart is known)
     */
    public Settlement(String id, int startChunkX, int startChunkZ, String factionId, String name, int x, int z) {
        this.id = Objects.requireNonNull(id);
        this.startChunkX = startChunkX;
        this.startChunkZ = startChunkZ;
        this.factionId = Objects.requireNonNull(factionId);
        this.name = Objects.requireNonNull(name);
        this.x = x;
        this.z = z;
    }

    public String id() {
        return id;
    }

    /** Only {@link SettlementRegistry#changeId(String, String)} changes the id. */
    void setId(String id) {
        this.id = Objects.requireNonNull(id);
    }

    public int startChunkX() {
        return startChunkX;
    }

    public int startChunkZ() {
        return startChunkZ;
    }

    public String factionId() {
        return factionId;
    }

    public void setFactionId(String factionId) {
        this.factionId = Objects.requireNonNull(factionId);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /** Block X of the heart, or of the structure start before the heart is known. */
    public int x() {
        return x;
    }

    /** Block Z of the heart, or of the structure start before the heart is known. */
    public int z() {
        return z;
    }

    /** True after the heart of the village loaded once. */
    public boolean hasHeart() {
        return hasHeart;
    }

    /** Block Y of the heart. The value is 0 if {@link #hasHeart()} is false. */
    public int heartY() {
        return heartY;
    }

    /**
     * Records the position of the heart. The position of the settlement becomes the position of the heart.
     *
     * @param x block X of the heart
     * @param y block Y of the heart
     * @param z block Z of the heart
     */
    public void setHeart(int x, int y, int z) {
        this.x = x;
        this.heartY = y;
        this.z = z;
        this.hasHeart = true;
    }

    public SettlementState state() {
        return state;
    }

    public void setState(SettlementState state) {
        this.state = Objects.requireNonNull(state);
    }

    /** True if the state is {@link SettlementState#ACTIVE}. */
    public boolean isActive() {
        return state == SettlementState.ACTIVE;
    }

    /** The level, from 1. */
    public int level() {
        return level;
    }

    /** The experience in the current level. */
    public int experience() {
        return experience;
    }

    /**
     * Sets the level and the experience.
     *
     * @param level      the level (1 or more)
     * @param experience the experience in this level (0 or more)
     */
    public void setProgress(int level, int experience) {
        if (level < 1 || experience < 0) {
            throw new IllegalArgumentException("Invalid progress: level " + level + ", experience " + experience);
        }
        this.level = level;
        this.experience = experience;
    }

    /** The biome id at the position of the settlement, or an empty text if it is not known. */
    public String biome() {
        return biome;
    }

    public void setBiome(String biome) {
        this.biome = Objects.requireNonNull(biome);
    }

    public EconomyState economy() {
        return economy;
    }

    /** Square of the horizontal distance from the settlement to a position, in blocks. */
    public long distanceSquared(int blockX, int blockZ) {
        long dx = (long) blockX - x;
        long dz = (long) blockZ - z;
        return dx * dx + dz * dz;
    }

    @Override
    public String toString() {
        return name + " (" + id + ", " + factionId + ", " + state.serializedName() + ", level " + level + ", " + x
                + " " + z + ")";
    }
}
