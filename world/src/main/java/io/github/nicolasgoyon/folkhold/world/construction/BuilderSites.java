package io.github.nicolasgoyon.folkhold.world.construction;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The construction sites of the builders of the villages (see {@link BuilderSite}). An operator starts a site with the
 * construction tool (see {@link ConstructionPlacement}) or with a command. Each level tick applies the work of the
 * builders on each site of the level.
 */
public final class BuilderSites {
    private BuilderSites() {
    }

    /**
     * The village of the builders of a structure: the nearest loaded village heart. The heart is at the village radius
     * from the center of the structure, or nearer (see {@link WorldConfig#VILLAGE_RADIUS}).
     *
     * @param level the level
     * @param box   the box of the structure
     * @return the position of the heart, or empty with no village near
     */
    public static Optional<BlockPos> villageFor(ServerLevel level, BoundingBox box) {
        BlockPos center = new BlockPos((box.minX() + box.maxX()) / 2, box.minY(), (box.minZ() + box.maxZ()) / 2);
        return VillageHearts.findNearest(level, center, WorldConfig.VILLAGE_RADIUS.get());
    }

    /**
     * Starts a construction site. The site comes after the other sites of the village.
     *
     * @param level    the level
     * @param template the id of the structure template
     * @param origin   the position of the origin of the template
     * @param rotation the turn of the template
     * @param mirror   the mirror of the template
     * @param heart    the position of the heart of the village of the builders
     * @param operator the player that gets the end of the construction, or null
     * @return the site
     */
    public static BuilderSite start(ServerLevel level, ResourceLocation template, BlockPos origin, Rotation rotation,
            Mirror mirror, BlockPos heart, @Nullable UUID operator) {
        return BuilderSiteData.get(level).add(template, origin, rotation, mirror, heart, operator);
    }

    /**
     * Starts a repair site (see {@link BuilderSite#repair}). The site comes after the other sites of the village.
     *
     * @param level     the level
     * @param heart     the position of the village heart
     * @param reference the structure pieces of the village
     * @param positions the damaged positions, 1 or more
     * @return the site
     */
    public static BuilderSite startRepair(ServerLevel level, BlockPos heart, VillageReference reference,
            Set<BlockPos> positions) {
        return BuilderSiteData.get(level).addRepair(heart, reference, positions);
    }

    /** The sites of a village, from the oldest to the newest. */
    public static List<BuilderSite> of(ServerLevel level, BlockPos heart) {
        return all(level).stream().filter(site -> site.heart().equals(heart)).toList();
    }

    /** The number of sites of the village of a site that come before it. */
    public static int sitesBefore(ServerLevel level, BuilderSite site) {
        int count = 0;
        for (BuilderSite other : all(level)) {
            if (other == site) {
                break;
            }
            if (other.heart().equals(site.heart())) {
                count++;
            }
        }
        return count;
    }

    /** The sites of a level, from the oldest to the newest. */
    public static List<BuilderSite> all(ServerLevel level) {
        BuilderSiteData data = BuilderSiteData.find(level);
        return data == null ? List.of() : List.copyOf(data.sites());
    }

    /**
     * Stops all the sites of a level. A site with ready chunks ends now: its fences and its scaffolding go away. The
     * other sites end when their chunks are ready.
     *
     * @param level the level
     * @return the number of stopped sites
     */
    public static int stop(ServerLevel level) {
        BuilderSiteData data = BuilderSiteData.find(level);
        if (data == null) {
            return 0;
        }
        List<BuilderSite> sites = List.copyOf(data.sites());
        for (BuilderSite site : sites) {
            if (site.stopNow(level)) {
                data.remove(site);
            }
        }
        data.setDirty();
        return sites.size();
    }

    /** Applies the work of the builders on the sites of the level. */
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BuilderSiteData data = BuilderSiteData.find(level);
        if (data == null || data.sites().isEmpty()) {
            return;
        }
        Set<BlockPos> villages = new HashSet<>();
        for (BuilderSite site : List.copyOf(data.sites())) {
            boolean first = villages.add(site.heart());
            if (site.tick(level, data, first)) {
                data.remove(site);
            }
        }
    }
}
