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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
 *       plan of the construction and starts its run ({@link #start}).</li>
 * </ol>
 * The server checks each request: the permission, the template, its size, and the distance to the operator.
 */
public final class ConstructionPlacement {
    /** A structure has this number of blocks at most, air included, as a capture of the commands. */
    public static final int MAX_VOLUME = 32768;
    /** The center of a placed structure is at this horizontal distance from the operator or nearer, in blocks. */
    public static final int MAX_DISTANCE = 96;
    /** A construction applies this number of steps for each tick at most. */
    public static final int MAX_STEPS_PER_TICK = 1000;
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
        if (!ReadyChunks.isAreaReady(level, box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
            status(player, Component.translatable(KEY + "not_loaded"), false);
            return false;
        }
        Target target = ConstructionTargets.fromTemplate(level, template.get(), request.origin(),
                GhostPlacement.settings(request.rotation(), request.mirror()));
        start(player.createCommandSourceStack(), level, target,
                Mth.clamp(request.stepsPerTick(), 1, MAX_STEPS_PER_TICK));
        status(player, Component.translatable(KEY + "started", request.template().toString()), true);
        return true;
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
        ConstructionRuns.start(run, () -> source.sendSuccess(() -> Component.translatable(COMMAND_KEY + "finished",
                run.total()), true));
        return plan.steps().size();
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
