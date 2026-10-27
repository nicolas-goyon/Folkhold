package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.core.sim.faction.CultureInfo;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BannerPattern;

/**
 * A culture: template of factions (colors, banner, name syllables, biomes of affinity).
 *
 * @param id           the culture id, for example {@code valeholm}
 * @param color        the primary color (RGB): tabards, map, messages
 * @param bannerBase   the base color of the banner
 * @param bannerLayers the pattern layers of the banner, bottom to top
 * @param prefixes     first syllables of the faction names
 * @param suffixes     last syllables of the faction names
 * @param voicePitch   the pitch of the voices of the units of the culture, as a multiple of the pitch of their sounds
 *                     (see {@link io.github.nicolasgoyon.folkhold.world.registry.ModSounds.UnitVoice})
 */
public record Culture(String id, int color, DyeColor bannerBase, List<BannerLayer> bannerLayers,
        List<String> prefixes, List<String> suffixes, float voicePitch) {

    /** One pattern layer of a banner. */
    public record BannerLayer(ResourceKey<BannerPattern> pattern, DyeColor color) {
    }

    /**
     * The biomes of affinity of the culture: the tag {@code folkhold_world:culture/<id>}. The culture is frequent in
     * the regions of these biomes (see {@link CultureMap}).
     */
    public TagKey<Biome> affinity() {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
                "culture/" + id));
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
