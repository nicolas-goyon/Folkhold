package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiReputation;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Visual elements of a faction: color, banner, name, and the texts of the reputation tiers of the players.
 */
public final class FactionVisuals {
    /** The translation key of the name of a reputation tier, followed by the serialized name of the tier. */
    public static final String TIER_KEY = "folkhold_world.reputation_tier_title.";

    private FactionVisuals() {
    }

    /** The name of a reputation tier with a capital letter: "Friend". */
    public static MutableComponent tierName(ReputationTier tier) {
        return Component.translatable(TIER_KEY + tier.serializedName());
    }

    /** The title of a reputation tier: its glyph and its name, "✓ Friend". The caller sets the color. */
    public static MutableComponent tierTitle(ReputationTier tier) {
        return Component.literal(UiReputation.glyph(tier) + " ").append(tierName(tier));
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

    /**
     * Gives the banner of a faction to a banner block. The block keeps its kind (standing or on a wall) and its
     * direction.
     *
     * @param level   the level
     * @param pos     the position of the banner block
     * @param faction the faction
     * @return true if the block is a banner
     */
    public static boolean paintBanner(Level level, BlockPos pos, FactionSpec faction) {
        BlockState state = level.getBlockState(pos);
        Optional<Culture> culture = Cultures.byId(faction.cultureId());
        if (!(state.getBlock() instanceof AbstractBannerBlock) || culture.isEmpty()) {
            return false;
        }
        DyeColor color = culture.get().bannerBase();
        BlockState painted;
        if (state.getBlock() instanceof WallBannerBlock) {
            painted = wallBanner(color).defaultBlockState()
                    .setValue(WallBannerBlock.FACING, state.getValue(WallBannerBlock.FACING));
        } else {
            painted = BannerBlock.byColor(color).defaultBlockState()
                    .setValue(BannerBlock.ROTATION, state.getValue(BannerBlock.ROTATION));
        }
        level.setBlock(pos, painted, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof BannerBlockEntity entity) {
            entity.fromItem(banner(level, faction), color);
            entity.setChanged();
            level.sendBlockUpdated(pos, painted, painted, Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /** The wall banner block of a color. */
    private static Block wallBanner(DyeColor color) {
        return BuiltInRegistries.BLOCK.stream()
                .filter(block -> block instanceof WallBannerBlock wall && wall.getColor() == color)
                .findFirst()
                .orElse(Blocks.WHITE_WALL_BANNER);
    }
}
