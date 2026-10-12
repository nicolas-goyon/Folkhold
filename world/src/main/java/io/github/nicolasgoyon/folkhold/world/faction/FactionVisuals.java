package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Visual elements of a faction: color, banner, name.
 */
public final class FactionVisuals {

    private FactionVisuals() {
    }

    /** The faction, if the id is known on this server. */
    public static Optional<FactionSpec> faction(@Nullable MinecraftServer server, @Nullable String factionId) {
        if (server == null || factionId == null) {
            return Optional.empty();
        }
        return FactionData.get(server).byId(factionId);
    }

    /** The name of the faction in its color. */
    public static MutableComponent name(FactionSpec faction) {
        return Component.literal(faction.name()).withStyle(style -> style.withColor(TextColor.fromRgb(faction.color())));
    }

    /**
     * The banner of the culture of a faction: base color and pattern layers.
     *
     * @param level   a level, for the banner pattern registry
     * @param faction the faction
     * @return the banner item
     */
    public static ItemStack banner(Level level, FactionSpec faction) {
        Optional<Culture> culture = Cultures.byId(faction.cultureId());
        if (culture.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack banner = new ItemStack(BannerBlock.byColor(culture.get().bannerBase()).asItem());
        Registry<BannerPattern> patterns = level.registryAccess().registryOrThrow(Registries.BANNER_PATTERN);
        BannerPatternLayers.Builder layers = new BannerPatternLayers.Builder();
        for (Culture.BannerLayer layer : culture.get().bannerLayers()) {
            patterns.getHolder(layer.pattern()).ifPresent(holder -> layers.add(holder, layer.color()));
        }
        banner.set(DataComponents.BANNER_PATTERNS, layers.build());
        return banner;
    }
}
