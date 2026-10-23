package io.github.nicolasgoyon.folkhold.world.client.renderer;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.entity.AnimatedVillagers;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * GeckoLib model of the animated villagers (see {@link AnimatedVillagers}): {@code geo/entity/animated_villager.geo.json}
 * and {@code animations/entity/animated_villager.animation.json}.
 *
 * <p>The model has the UV layout of the vanilla villager model, so the vanilla textures fit it: the skin, the texture
 * of the villager type and the texture of the profession. The arms are separate. Each arm has a sleeve (the texture
 * area of the crossed arms) and a hand (the texture area of the hands of the crossed arms). The head bone follows the
 * look direction.
 */
public class AnimatedVillagerModel extends DefaultedEntityGeoModel<AnimatedVillagers> {
    /** The skin of the villagers, under the type and the profession. */
    static final ResourceLocation SKIN = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");

    public AnimatedVillagerModel() {
        super(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "animated_villager"), true);
    }

    @Override
    public ResourceLocation getTextureResource(AnimatedVillagers animatable) {
        return SKIN;
    }
}
