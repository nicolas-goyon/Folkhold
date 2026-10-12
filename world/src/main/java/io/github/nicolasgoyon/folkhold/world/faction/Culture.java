package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.core.sim.faction.CultureInfo;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;

/**
 * A culture: template of factions (colors, banner, name syllables).
 *
 * @param id           the culture id, for example {@code valeholm}
 * @param color        the primary color (RGB): tabards, map, messages
 * @param bannerBase   the base color of the banner
 * @param bannerLayers the pattern layers of the banner, bottom to top
 * @param prefixes     first syllables of the faction names
 * @param suffixes     last syllables of the faction names
 */
public record Culture(String id, int color, DyeColor bannerBase, List<BannerLayer> bannerLayers,
        List<String> prefixes, List<String> suffixes) {

    /** One pattern layer of a banner. */
    public record BannerLayer(ResourceKey<BannerPattern> pattern, DyeColor color) {
    }

    /** Translation key of the culture name, for example {@code folkhold_world.culture.valeholm}. */
    public String translationKey() {
        return "folkhold_world.culture." + id;
    }

    /** The data that the faction generator in {@code core} needs. */
    public CultureInfo info() {
        return new CultureInfo(id, color, prefixes, suffixes);
    }
}
