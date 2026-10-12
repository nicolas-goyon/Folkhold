package io.github.nicolasgoyon.folkhold.world.client.renderer;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * GeckoLib model of the faction units. One humanoid model and one animation file for all unit types:
 * {@code geo/entity/faction_unit.geo.json} and {@code animations/entity/faction_unit.animation.json}.
 *
 * <p>The texture is one of the 9 vanilla player skins, selected from the id of the unit. The model has the
 * same UV layout as the vanilla player model. The head bone follows the look direction.
 */
public class FactionUnitModel<T extends FactionUnit> extends DefaultedEntityGeoModel<T> {

    private static final ResourceLocation[] SKINS = {
            skin("steve"), skin("alex"), skin("ari"), skin("efe"), skin("kai"),
            skin("makena"), skin("noor"), skin("sunny"), skin("zuri"),
    };

    public FactionUnitModel() {
        super(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "faction_unit"), true);
    }

    private static ResourceLocation skin(String name) {
        return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureResource(T unit) {
        return SKINS[Math.floorMod(unit.getUUID().hashCode(), SKINS.length)];
    }
}
