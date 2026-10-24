package io.github.nicolasgoyon.folkhold.world.construction;

import io.github.nicolasgoyon.folkhold.core.sim.construction.ConstructionPlanner;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Plan;
import io.github.nicolasgoyon.folkhold.core.sim.construction.PlanWarning;
import io.github.nicolasgoyon.folkhold.core.sim.construction.StepType;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Target;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.admin.AdminActions;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets.TemplateBlock;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionEndPayload;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionPlacePayload;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionPreviewPayload;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionPreviewPayload.Entry;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionStatusPayload;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionTemplatesPayload;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The construction tool of the operators (permission level 2): a menu of the structure templates, a ghost of a
 * template in the level, and the start of the construction at the position of the ghost.
 *
 * <ol>
 *   <li>The command {@code /folkhold construction place} sends the list of the templates ({@link #openMenu}).</li>
 *   <li>The client asks for the blocks of a template ({@link #sendPreview}), and shows the ghost of the template.</li>
 *   <li>The client sends the position, the turn and the mirror of the ghost ({@link #place}). The server makes the
 *       plan of the construction and starts its run ({@link #start}). With the speed {@link #BUILDERS}, the builders
 *       of the nearest village build the structure instead ({@link #startBuilders}).</li>
 * </ol>
 * The server checks each request: the permission, the template, its size, the distance to the operator and the
 * chunks around the structure ({@link #isAreaReady}). At the end of the construction, the server sends its end to the
 * operator: the client removes the ghost.
 */
public final class ConstructionPlacement {
    /** A structure has this number of blocks at most, air included, as a capture of the commands. */
    public static final int MAX_VOLUME = 32768;
    /** The center of a placed structure is at this horizontal distance from the operator or nearer, in blocks. */
    public static final int MAX_DISTANCE = 96;
    /** A construction applies this number of steps for each tick at most. */
    public static final int MAX_STEPS_PER_TICK = 1000;
    /** The speed of a placement that the builders of the nearest village build (see {@link BuilderSites}). */
    public static final int BUILDERS = 0;
    private static final String KEY = "message.folkhold_world.construction.";
    private static final String COMMAND_KEY = "commands.folkhold_world.construction.";
    /** The chat shows this number of warnings of a plan at most. */
    private static final int SHOWN_WARNINGS = 8;

    private ConstructionPlacement() {
    }

    /**
     * Sends the structure templates of the server to an operator. The client opens the construction menu. The
     * templates of the mod come first.
     *
     * @param player the player
     */
    public static void openMenu(ServerPlayer player) {
        if (!allowed(player)) {
            status(player, Component.translatable(KEY + "not_operator"), false);
            return;
        }
        List<ResourceLocation> templates = player.serverLevel().getStructureManager().listTemplates()
                .distinct()
                .sorted(Comparator.comparing((ResourceLocation id) -> !id.getNamespace().equals(FolkholdWorld.MOD_ID))
                        .thenComparing(ResourceLocation::toString))
                .toList();
        ModNetwork.send(player, new ConstructionTemplatesPayload(templates));
    }

    /**
     * Sends the blocks of a structure template to an operator, for the ghost.
     *
     * @param player the player
     * @param id     the id of the template
     */
    public static void sendPreview(ServerPlayer player, ResourceLocation id) {
        ServerLevel level = player.serverLevel();
        Optional<StructureTemplate> template = checkedTemplate(player, id);
        if (template.isPresent()) {
            ModNetwork.send(player, preview(level, id, template.get()));
        }
    }

    /**
     * The blocks of a structure template for the ghost: the blocks that are not air, in the coordinates of the
     * template, with no turn and no mirror. A jigsaw block has its final state, as in the target of a construction.
     *
     * @param level    the level: it gives the blocks
     * @param id       the id of the template
     * @param template the template
     * @return the payload
     */
    public static ConstructionPreviewPayload preview(ServerLevel level, ResourceLocation id,
            StructureTemplate template) {
        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        List<Entry> blocks = new ArrayList<>();
        for (TemplateBlock block : ConstructionTargets.templateBlocks(level, template)) {
            if (block.state().isAir()) {
                continue;
            }
            int index = palette.computeIfAbsent(block.state(), state -> palette.size());
            blocks.add(new Entry(block.pos().getX(), block.pos().getY(), block.pos().getZ(), index));
        }
        return new ConstructionPreviewPayload(id, template.getSize(), List.copyOf(palette.keySet()), blocks);
    }

    /**
     * Starts the construction of a structure template at the position of the ghost of an operator.
     *
     * @param player  the player
     * @param request the template, its origin, its turn and its mirror
     * @return true if the construction started
     */
    public static boolean place(ServerPlayer player, ConstructionPlacePayload request) {
        Optional<StructureTemplate> template = checkedTemplate(player, request.template());
        if (template.isEmpty()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        BoundingBox box = GhostPlacement.box(template.get().getSize(), request.rotation(), request.mirror(),
                request.origin());
        double dx = (box.minX() + box.maxX() + 1) / 2.0 - player.getX();
        double dz = (box.minZ() + box.maxZ() + 1) / 2.0 - player.getZ();
        if (dx * dx + dz * dz > (double) MAX_DISTANCE * MAX_DISTANCE) {
            status(player, Component.translatable(KEY + "too_far", MAX_DISTANCE), false);
            return false;
        }
        if (level.isOutsideBuildHeight(box.minY()) || level.isOutsideBuildHeight(box.maxY())) {
            status(player, Component.translatable(KEY + "outside_height"), false);
            return false;
        }
        if (!isAreaReady(level, box)) {
            status(player, Component.translatable(KEY + "not_loaded"), false);
            return false;
        }
        if (request.stepsPerTick() == BUILDERS) {
            BuildersAnswer answer = startBuilders(level, request.template(), request.origin(), request.rotation(),
                    request.mirror(), box, player.getUUID());
            status(player, answer.message(), answer.started());
            return answer.started();
        }
        Target target = ConstructionTargets.fromTemplate(level, template.get(), request.origin(),
                GhostPlacement.settings(request.rotation(), request.mirror()));
        MinecraftServer server = level.getServer();
        UUID playerId = player.getUUID();
        start(player.createCommandSourceStack(), level, target,
                Mth.clamp(request.stepsPerTick(), 1, MAX_STEPS_PER_TICK),
                finished -> sendEnd(server, playerId,
                        new ConstructionEndPayload(request.template(), request.origin(), finished)));
        status(player, Component.translatable(KEY + "started", request.template().toString()), true);
        return true;
    }

    /**
     * True if the chunks of a construction are ready (see {@link ReadyChunks}): the box of its target, and the
     * positions around the box where a builder stands or where the plan puts scaffolding. A plan reads these
     * positions, and a read in a chunk that is not ready waits on the server thread.
     *
     * @param level the level
     * @param box   the box of the target
     * @return true if the plan of the construction does not wait for a chunk
     */
    public static boolean isAreaReady(ServerLevel level, BoundingBox box) {
        int margin = WorldConfig.constructionReach().horizontal() + 1;
        return ReadyChunks.isAreaReady(level, box.minX() - margin, box.minZ() - margin, box.maxX() + margin,
                box.maxZ() + margin);
    }

    /**
     * Makes the plan of a target, shows the plan to the source, and starts its run. The chat shows the number of steps
     * and the warnings of the plan, and a message at the end of the run.
     *
     * @param source       the source: it gets the messages
     * @param level        the level of the construction
     * @param target       the target
     * @param stepsPerTick the number of steps for each tick, 1 or more
     * @return the number of steps of the plan
     */
    public static int start(CommandSourceStack source, ServerLevel level, Target target, int stepsPerTick) {
        return start(source, level, target, stepsPerTick, finished -> {
        });
    }

    /**
     * As {@link #start(CommandSourceStack, ServerLevel, Target, int)}, with an action at the end of the run.
     *
     * @param source       the source: it gets the messages
     * @param level        the level of the construction
     * @param target       the target
     * @param stepsPerTick the number of steps for each tick, 1 or more
     * @param onEnd        the action at the end of the run: when the run applied all its steps, or when a command
     *                     stopped it
     * @return the number of steps of the plan
     */
    public static int start(CommandSourceStack source, ServerLevel level, Target target, int stepsPerTick,
            ConstructionRuns.EndAction onEnd) {
        Plan plan = ConstructionPlanner.plan(target, ConstructionTargets.view(level), WorldConfig.constructionReach());
        source.sendSuccess(() -> Component.translatable(COMMAND_KEY + "plan", plan.steps().size(),
                plan.count(StepType.REMOVE), plan.count(StepType.PLACE), plan.count(StepType.PLACE_SCAFFOLDING),
                plan.count(StepType.ADD_FLUID), plan.warnings().size()), true);
        for (PlanWarning warning : plan.warnings().subList(0, Math.min(SHOWN_WARNINGS, plan.warnings().size()))) {
            source.sendSuccess(() -> Component.translatable(COMMAND_KEY + "warning."
                    + warning.problem().name().toLowerCase(Locale.ROOT), warning.pos().x(), warning.pos().y(),
                    warning.pos().z()), false);
        }
        if (plan.warnings().size() > SHOWN_WARNINGS) {
            source.sendSuccess(() -> Component.translatable(COMMAND_KEY + "more_warnings",
                    plan.warnings().size() - SHOWN_WARNINGS), false);
        }
        ConstructionRun run = new ConstructionRun(level, plan, stepsPerTick);
        ConstructionRuns.start(run, finished -> {
            if (finished) {
                source.sendSuccess(() -> Component.translatable(COMMAND_KEY + "finished", run.total()), true);
            }
            onEnd.ended(finished);
        });
        return plan.steps().size();
    }

    /**
     * The answer to the start of a construction site of the builders.
     *
     * @param site    the site, or empty when the site did not start
     * @param message the message for the operator
     */
    public record BuildersAnswer(Optional<BuilderSite> site, Component message) {
        /** True if the site started. */
        public boolean started() {
            return site.isPresent();
        }
    }

    /**
     * Starts a construction site of the builders of the nearest village (see {@link BuilderSites}). The site comes
     * after the other sites of the village.
     *
     * @param level    the level
     * @param template the id of the structure template
     * @param origin   the position of the origin of the template
     * @param rotation the turn of the template
     * @param mirror   the mirror of the template
     * @param box      the box of the structure
     * @param operator the player that gets the end of the construction, or null
     * @return the answer for the operator
     */
    public static BuildersAnswer startBuilders(ServerLevel level, ResourceLocation template, BlockPos origin,
            Rotation rotation, Mirror mirror, BoundingBox box, @Nullable UUID operator) {
        if (WorldConfig.BUILDERS.get() <= 0) {
            return new BuildersAnswer(Optional.empty(), Component.translatable(KEY + "no_builders"));
        }
        Optional<BlockPos> heart = BuilderSites.villageFor(level, box);
        if (heart.isEmpty()) {
            return new BuildersAnswer(Optional.empty(), Component.translatable(KEY + "no_village"));
        }
        BuilderSite site = BuilderSites.start(level, template, origin, rotation, mirror, heart.get(), operator);
        int before = BuilderSites.sitesBefore(level, site);
        Component message = before == 0
                ? Component.translatable(KEY + "builders_started", template.toString())
                : Component.translatable(KEY + "builders_queued", template.toString(), before);
        return new BuildersAnswer(Optional.of(site), message);
    }

    /** Sends the end of a construction to the operator that started it, if the operator is online. */
    private static void sendEnd(MinecraftServer server, UUID playerId, ConstructionEndPayload payload) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            ModNetwork.send(player, payload);
        }
    }

    /** True if the player can use the construction tool. */
    public static boolean allowed(ServerPlayer player) {
        return player.hasPermissions(AdminActions.PERMISSION_LEVEL);
    }

    /**
     * The template of a request, after the checks of the permission and of the size. A failed check sends an error to
     * the player.
     */
    private static Optional<StructureTemplate> checkedTemplate(ServerPlayer player, ResourceLocation id) {
        if (!allowed(player)) {
            status(player, Component.translatable(KEY + "not_operator"), false);
            return Optional.empty();
        }
        Optional<StructureTemplate> template = player.serverLevel().getStructureManager().get(id);
        Vec3i size = template.map(StructureTemplate::getSize).orElse(Vec3i.ZERO);
        long volume = (long) size.getX() * size.getY() * size.getZ();
        if (volume <= 0) {
            status(player, Component.translatable(COMMAND_KEY + "unknown_template", id.toString()), false);
            return Optional.empty();
        }
        if (volume > MAX_VOLUME) {
            status(player, Component.translatable(KEY + "too_large", volume, MAX_VOLUME), false);
            return Optional.empty();
        }
        return template;
    }

    /** Sends an answer to the player: the client shows it in the menu or above the hotbar. */
    private static void status(ServerPlayer player, Component text, boolean success) {
        if (!ModNetwork.send(player, new ConstructionStatusPayload(text, success))) {
            player.sendSystemMessage(text);
        }
    }
}
