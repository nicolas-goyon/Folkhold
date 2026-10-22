package io.github.nicolasgoyon.folkhold.world.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nicolasgoyon.folkhold.world.entity.AnimatedVillagers;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.metadata.animation.VillagerMetaDataSection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Renders the animated villagers (see {@link AnimatedVillagers}) with the GeckoLib model of
 * {@link AnimatedVillagerModel}. The other villagers keep the vanilla renderer.
 *
 * <ul>
 *   <li>The vanilla villager renderer stays registered. Before it draws an animated villager, the event
 *       {@link RenderLivingEvent.Pre} gives the villager to this renderer ({@link #onRenderLiving}). So the other
 *       villagers keep the vanilla renderer, and the renderers of the other mods.</li>
 *   <li>The model has three textures, as the vanilla villager: the skin, the type (biome) and the profession. The
 *       hat of the type shows only when the profession has no hat, as in the vanilla layer.</li>
 *   <li>The hands keep the skin: the type and the profession textures do not cover them.</li>
 * </ul>
 */
public class AnimatedVillagerRenderer extends GeoReplacedEntityRenderer<Villager, AnimatedVillagers> {
    private static final List<String> HANDS = List.of("rightHand", "leftHand");
    private static final String HEAD = "head";

    @Nullable
    private static AnimatedVillagerRenderer instance;

    public AnimatedVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new AnimatedVillagerModel(), AnimatedVillagers.get());
        this.shadowRadius = 0.5F;
        addRenderLayer(new ClothesLayer(this));
    }

    /** Creates the renderer. The game calls it at each reload of the resources. */
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        instance = new AnimatedVillagerRenderer(event.getContext());
    }

    /** Draws an animated villager with this renderer, in place of the vanilla renderer. */
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        AnimatedVillagerRenderer renderer = instance;
        if (renderer == null || !(event.getEntity() instanceof Villager villager)
                || !AnimatedVillagers.isAnimated(villager)) {
            return;
        }
        event.setCanceled(true);
        float partialTick = event.getPartialTick();
        renderer.render(villager, Mth.rotLerp(partialTick, villager.yRotO, villager.getYRot()), partialTick,
                event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        postAfter(event);
    }

    /**
     * Sends the event after the rendering, as the vanilla renderer does: the other listeners (for example the marker
     * of a quest target) also work for an animated villager.
     */
    private static <T extends LivingEntity, M extends EntityModel<T>> void postAfter(RenderLivingEvent.Pre<T, M> pre) {
        NeoForge.EVENT_BUS.post(new RenderLivingEvent.Post<>(pre.getEntity(), pre.getRenderer(), pre.getPartialTick(),
                pre.getPoseStack(), pre.getMultiBufferSource(), pre.getPackedLight()));
    }

    /** The texture of the villager type or of the profession of a villager, as in the vanilla layer. */
    private static ResourceLocation texture(String folder, ResourceLocation id) {
        return id.withPath(path -> "textures/entity/villager/" + folder + "/" + path + ".png");
    }

    /**
     * The type and the profession textures, drawn on the model. The hands are hidden during these passes, so they
     * keep the skin.
     */
    private static final class ClothesLayer extends GeoRenderLayer<AnimatedVillagers> {
        private final AnimatedVillagerRenderer villagerRenderer;
        private final Map<ResourceLocation, VillagerMetaDataSection.Hat> hats = new HashMap<>();

        ClothesLayer(AnimatedVillagerRenderer renderer) {
            super(renderer);
            this.villagerRenderer = renderer;
        }

        @Override
        public void render(PoseStack poseStack, AnimatedVillagers animatable, BakedGeoModel bakedModel,
                @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                float partialTick, int packedLight, int packedOverlay) {
            Villager villager = villagerRenderer.getCurrentEntity();
            if (villager == null) {
                return;
            }
            VillagerData data = villager.getVillagerData();
            ResourceLocation type = texture("type", BuiltInRegistries.VILLAGER_TYPE.getKey(data.getType()));
            ResourceLocation profession = texture("profession",
                    BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession()));
            VillagerMetaDataSection.Hat typeHat = hat(type);
            VillagerMetaDataSection.Hat professionHat = hat(profession);
            boolean typeHead = professionHat == VillagerMetaDataSection.Hat.NONE
                    || (professionHat == VillagerMetaDataSection.Hat.PARTIAL
                            && typeHat != VillagerMetaDataSection.Hat.FULL);

            setHidden(bakedModel, HANDS, true);
            if (!typeHead) {
                setHidden(bakedModel, List.of(HEAD), true);
            }
            draw(poseStack, animatable, bakedModel, bufferSource, type, partialTick, packedLight, packedOverlay);
            setHidden(bakedModel, List.of(HEAD), false);
            draw(poseStack, animatable, bakedModel, bufferSource, profession, partialTick, packedLight,
                    packedOverlay);
            setHidden(bakedModel, HANDS, false);
        }

        private void draw(PoseStack poseStack, AnimatedVillagers animatable, BakedGeoModel bakedModel,
                MultiBufferSource bufferSource, ResourceLocation texture, float partialTick, int packedLight,
                int packedOverlay) {
            RenderType type = RenderType.entityCutoutNoCull(texture);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type,
                    bufferSource.getBuffer(type), partialTick, packedLight, packedOverlay, -1);
        }

        private static void setHidden(BakedGeoModel bakedModel, List<String> bones, boolean hidden) {
            for (String name : bones) {
                bakedModel.getBone(name).ifPresent((GeoBone bone) -> bone.setHidden(hidden));
            }
        }

        /** The hat of a texture, from its metadata, as in the vanilla layer. */
        private VillagerMetaDataSection.Hat hat(ResourceLocation texture) {
            return hats.computeIfAbsent(texture, id -> {
                Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
                try {
                    if (resource.isPresent()) {
                        return resource.get().metadata().getSection(VillagerMetaDataSection.SERIALIZER)
                                .map(VillagerMetaDataSection::getHat).orElse(VillagerMetaDataSection.Hat.NONE);
                    }
                } catch (IOException e) {
                    return VillagerMetaDataSection.Hat.NONE;
                }
                return VillagerMetaDataSection.Hat.NONE;
            });
        }
    }
}
