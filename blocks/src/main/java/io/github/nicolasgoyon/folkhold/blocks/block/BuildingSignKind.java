package io.github.nicolasgoyon.folkhold.blocks.block;

import java.util.Locale;

/**
 * The kinds of building signs. Each kind has its own block ({@code building_sign_<name>}). The script
 * {@code tools/textures/building_signs.py} makes the resources of each kind: it must have the same kinds.
 */
public enum BuildingSignKind {
    /** A house of the inhabitants. */
    HOUSE,
    /** The barracks of the garrison. */
    BARRACKS,
    /** The forge. */
    FORGE,
    /** The storehouse. */
    STOREHOUSE,
    /** The bank. */
    BANK,
    /** The village hall: the plaza of the village heart. */
    HALL,
    /** A farm. */
    FARM,
    /** A market. */
    MARKET;

    /** The name in the block id, for example {@code house}. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The block id path, for example {@code building_sign_house}. */
    public String blockId() {
        return "building_sign_" + serializedName();
    }
}
