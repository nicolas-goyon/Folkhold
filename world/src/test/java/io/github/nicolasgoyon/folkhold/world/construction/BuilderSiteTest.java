package io.github.nicolasgoyon.folkhold.world.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

/** The saved data of a construction site of the builders. */
class BuilderSiteTest {
    private static final ResourceLocation COTTAGE =
            ResourceLocation.fromNamespaceAndPath("folkhold_world", "village/house_cottage");

    private static BuilderSite site() {
        return new BuilderSite(7, COTTAGE, new BlockPos(10, 64, -20), Rotation.CLOCKWISE_90, Mirror.FRONT_BACK,
                new BlockPos(0, 64, 0), new UUID(1L, 2L));
    }

    /** A site keeps its template, its position, its phase, its fences and its scaffolding after a load. */
    @Test
    void siteKeepsItsDataAfterALoad() {
        CompoundTag tag = site().save();
        tag.putString("Phase", "build");
        tag.putLongArray("Fences", new long[] {BlockPos.asLong(1, 64, 2), BlockPos.asLong(1, 64, 3)});
        tag.putLongArray("Scaffolding", new long[] {BlockPos.asLong(5, 65, 5)});

        BuilderSite loaded = BuilderSite.load(tag).orElseThrow();

        assertEquals(7, loaded.id());
        assertEquals(COTTAGE, loaded.template());
        assertEquals(new BlockPos(10, 64, -20), loaded.origin());
        assertEquals(new BlockPos(0, 64, 0), loaded.heart());
        assertEquals(BuilderSite.Phase.BUILD, loaded.phase());
        assertEquals(Set.of(new BlockPos(1, 64, 2), new BlockPos(1, 64, 3)), loaded.fences());
        assertEquals(Set.of(new BlockPos(5, 65, 5)), loaded.scaffolding());
        assertEquals(tag, loaded.save(), "A site saves the data that it loaded");
    }

    /** The data of a site with no template, or with an unknown turn or phase, gives no site. */
    @Test
    void dataThatIsNotValidGivesNoSite() {
        CompoundTag noTemplate = site().save();
        noTemplate.remove("Template");
        CompoundTag unknownRotation = site().save();
        unknownRotation.putString("Rotation", "SIDEWAYS");
        CompoundTag unknownPhase = site().save();
        unknownPhase.putString("Phase", "painting");

        assertTrue(BuilderSite.load(noTemplate).isEmpty());
        assertTrue(BuilderSite.load(unknownRotation).isEmpty());
        assertTrue(BuilderSite.load(unknownPhase).isEmpty());
    }
}
