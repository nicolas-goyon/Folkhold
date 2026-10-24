package io.github.nicolasgoyon.folkhold.world.faction;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatterns;

/**
 * The cultures of the world mod: Java definitions (see {@code docs/design.md}, section "Cultures").
 */
public final class Cultures {
    /** Blue, spruce and stone. Northern names. */
    public static final Culture VALEHOLM = new Culture("valeholm", 0x3C5A9A, DyeColor.BLUE,
            List.of(new Culture.BannerLayer(BannerPatterns.STRIPE_CENTER, DyeColor.WHITE),
                    new Culture.BannerLayer(BannerPatterns.BORDER, DyeColor.LIGHT_BLUE)),
            List.of("val", "hal", "brim", "ost", "ald", "thor"),
            List.of("holm", "gard", "mere", "wick", "stead", "vik"), 1.0F);

    /** Red, dark wood. Harsh names, low voices. */
    public static final Culture ASHMARK = new Culture("ashmark", 0x9A3C3C, DyeColor.RED,
            List.of(new Culture.BannerLayer(BannerPatterns.CROSS, DyeColor.BLACK),
                    new Culture.BannerLayer(BannerPatterns.STRIPE_BOTTOM, DyeColor.BLACK)),
            List.of("ash", "kar", "dren", "mor", "vel", "grim"),
            List.of("mark", "haven", "fell", "crag", "moor", "hold"), 0.9F);

    /** Gold, sandstone. Bright names, high voices. */
    public static final Culture SUNREACH = new Culture("sunreach", 0xC8A030, DyeColor.YELLOW,
            List.of(new Culture.BannerLayer(BannerPatterns.CIRCLE_MIDDLE, DyeColor.ORANGE),
                    new Culture.BannerLayer(BannerPatterns.STRIPE_TOP, DyeColor.WHITE)),
            List.of("sol", "ar", "lum", "cal", "ser", "eli"),
            List.of("reach", "ford", "vale", "dale", "spire", "mont"), 1.1F);

    public static final List<Culture> ALL = List.of(VALEHOLM, ASHMARK, SUNREACH);

    private Cultures() {
    }

    /**
     * Finds a culture by id.
     *
     * @param id the culture id
     * @return the culture, or empty if the id is unknown
     */
    public static Optional<Culture> byId(String id) {
        return ALL.stream().filter(culture -> culture.id().equals(id)).findFirst();
    }
}
