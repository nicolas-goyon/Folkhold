package io.github.nicolasgoyon.folkhold.core.client;

import io.github.nicolasgoyon.folkhold.core.FolkholdCore;
import io.github.nicolasgoyon.folkhold.core.changelog.Changelog;
import io.github.nicolasgoyon.folkhold.core.changelog.WhatsNew;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client entry point of the core mod. The game loads this class only on the client.
 *
 * <p>The "What's new" screen ({@link WhatsNewScreen}) opens one time after an update, in place of the first title
 * screen. The button "Config" of Folkhold Core in the mod list opens it again.
 */
@Mod(value = FolkholdCore.MOD_ID, dist = Dist.CLIENT)
public final class FolkholdCoreClient {
    private static final int VERSIONS = 5;

    private final Changelog changelog;
    private final String version;
    private boolean checked;

    public FolkholdCoreClient(IEventBus modEventBus, ModContainer modContainer) {
        this.changelog = load(modContainer);
        this.version = modContainer.getModInfo().getVersion().toString();
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new WhatsNewScreen(changelog.newest(VERSIONS), parent));
        NeoForge.EVENT_BUS.addListener(this::onScreenOpening);
    }

    /** The changelog in the jar. A missing or bad file gives an empty changelog and, for a bad file, a warning. */
    private static Changelog load(ModContainer modContainer) {
        try (Reader reader = Files.newBufferedReader(
                modContainer.getModInfo().getOwningFile().getFile().findResource(Changelog.RESOURCE.split("/")),
                StandardCharsets.UTF_8)) {
            return Changelog.parse(reader);
        } catch (NoSuchFileException e) {
            FolkholdCore.LOGGER.debug("No changelog in the jar: {}", Changelog.RESOURCE);
        } catch (IOException | RuntimeException e) {
            FolkholdCore.LOGGER.warn("Cannot read the changelog {}: {}", Changelog.RESOURCE, e.getMessage());
        }
        return Changelog.EMPTY;
    }

    /** The first title screen of the session: the "What's new" screen opens in its place after an update. */
    private void onScreenOpening(ScreenEvent.Opening event) {
        if (checked || changelog.isEmpty() || !(event.getNewScreen() instanceof TitleScreen title)) {
            return;
        }
        checked = true;
        Path file = FMLPaths.CONFIGDIR.get().resolve(WhatsNew.FILE);
        if (WhatsNew.shouldShow(WhatsNew.read(file), version, changelog)) {
            event.setNewScreen(new WhatsNewScreen(changelog.newest(VERSIONS), title));
            try {
                WhatsNew.write(file, version);
            } catch (IOException e) {
                FolkholdCore.LOGGER.warn("Cannot write {}: {}", file, e.getMessage());
            }
        }
    }
}
