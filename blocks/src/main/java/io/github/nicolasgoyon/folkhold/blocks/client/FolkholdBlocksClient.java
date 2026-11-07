package io.github.nicolasgoyon.folkhold.blocks.client;

import io.github.nicolasgoyon.folkhold.blocks.FolkholdBlocks;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client entry point of the library. The game loads this class only on the client. */
@Mod(value = FolkholdBlocks.MOD_ID, dist = Dist.CLIENT)
public final class FolkholdBlocksClient {
    public FolkholdBlocksClient(IEventBus modEventBus) {
        modEventBus.addListener(FolkholdBlocksClient::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.JOB_SIGN.get(), JobSignRenderer::new);
    }
}
