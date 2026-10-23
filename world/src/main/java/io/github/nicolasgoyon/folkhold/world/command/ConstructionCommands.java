package io.github.nicolasgoyon.folkhold.world.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Target;
import io.github.nicolasgoyon.folkhold.world.admin.AdminActions;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionPlacement;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionRuns;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Commands of the construction plans (permission level 2):
 * <ul>
 *   <li>{@code /folkhold construction build <template> [stepsPerTick]}: builds a structure template step by step. The
 *       origin of the template is the position of the source.</li>
 *   <li>{@code /folkhold construction build <template> builders}: the builders of the nearest village build a
 *       structure template (see {@link BuilderSites}). The origin of the template is the position of the source.</li>
 *   <li>{@code /folkhold construction place [template]}: opens the construction menu of the player, or shows the ghost
 *       of a template. The player moves the ghost, then starts the construction (see
 *       {@link ConstructionPlacement}).</li>
 *   <li>{@code /folkhold construction capture <from> <to>}: keeps the blocks of a box, as the target of
 *       {@code restore}.</li>
 *   <li>{@code /folkhold construction restore [stepsPerTick]}: puts back the blocks of the captured box, step by
 *       step.</li>
 *   <li>{@code /folkhold construction sites}: shows the construction sites of the builders in the level of the
 *       source.</li>
 *   <li>{@code /folkhold construction stop}: stops the constructions and the construction sites in the level of the
 *       source. The fences and the scaffolding of a site go away.</li>
 * </ul>
 * A command that starts a construction shows the plan: its steps and its warnings. The construction shows a message
 * at its end.
 */
public final class ConstructionCommands {
    private static final String KEY = "commands.folkhold_world.construction.";
    /** The message of a structure in a chunk that is not ready, as for the ghost. */
    private static final String NOT_LOADED = "message.folkhold_world.construction.not_loaded";
    /** A capture has this number of blocks at most, as the vanilla command {@code /fill}. */
    private static final int MAX_CAPTURE = ConstructionPlacement.MAX_VOLUME;
    private static final int DEFAULT_STEPS_PER_TICK = 4;

    /** The captured box of the server, or null with no capture. */
    @Nullable
    private static Capture capture;

    private ConstructionCommands() {
    }

    /** A captured box: its level, its corners and its blocks. */
    private record Capture(ResourceKey<Level> dimension, BoundingBox box, Target target) {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("folkhold")
                .then(Commands.literal("construction")
                        .requires(source -> source.hasPermission(AdminActions.PERMISSION_LEVEL))
                        .then(Commands.literal("build")
                                .then(Commands.argument("template", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                                context.getSource().getLevel().getStructureManager().listTemplates(),
                                                builder))
                                        .executes(context -> build(context, DEFAULT_STEPS_PER_TICK))
                                        .then(Commands.literal("builders")
                                                .executes(ConstructionCommands::buildWithBuilders))
                                        .then(stepsPerTick()
                                                .executes(context -> build(context, stepsPerTick(context))))))
                        .then(Commands.literal("place")
                                .executes(ConstructionCommands::openMenu)
                                .then(Commands.argument("template", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                                context.getSource().getLevel().getStructureManager().listTemplates(),
                                                builder))
                                        .executes(ConstructionCommands::place)))
                        .then(Commands.literal("capture")
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(ConstructionCommands::capture))))
                        .then(Commands.literal("restore")
                                .executes(context -> restore(context, DEFAULT_STEPS_PER_TICK))
                                .then(stepsPerTick().executes(context -> restore(context, stepsPerTick(context)))))
                        .then(Commands.literal("sites").executes(ConstructionCommands::sites))
                        .then(Commands.literal("stop").executes(ConstructionCommands::stop))));
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        capture = null;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> stepsPerTick() {
        return Commands.argument("stepsPerTick",
                IntegerArgumentType.integer(1, ConstructionPlacement.MAX_STEPS_PER_TICK));
    }

    private static int stepsPerTick(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "stepsPerTick");
    }

    private static int build(CommandContext<CommandSourceStack> context, int stepsPerTick) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ResourceLocation id = ResourceLocationArgument.getId(context, "template");
        Optional<StructureTemplate> template = level.getStructureManager().get(id);
        if (template.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "unknown_template", id.toString()));
            return 0;
        }
        BlockPos origin = BlockPos.containing(source.getPosition());
        StructurePlaceSettings settings = new StructurePlaceSettings();
        if (!ConstructionPlacement.isAreaReady(level, template.get().getBoundingBox(settings, origin))) {
            source.sendFailure(Component.translatable(NOT_LOADED));
            return 0;
        }
        Target target = ConstructionTargets.fromTemplate(level, template.get(), origin, settings);
        return ConstructionPlacement.start(source, level, target, stepsPerTick);
    }

    private static int buildWithBuilders(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ResourceLocation id = ResourceLocationArgument.getId(context, "template");
        Optional<StructureTemplate> template = level.getStructureManager().get(id);
        if (template.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "unknown_template", id.toString()));
            return 0;
        }
        BlockPos origin = BlockPos.containing(source.getPosition());
        BoundingBox box = template.get().getBoundingBox(new StructurePlaceSettings(), origin);
        if (!ConstructionPlacement.isAreaReady(level, box)) {
            source.sendFailure(Component.translatable(NOT_LOADED));
            return 0;
        }
        UUID operator = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
        ConstructionPlacement.BuildersAnswer answer = ConstructionPlacement.startBuilders(level, id, origin,
                Rotation.NONE, Mirror.NONE, box, operator);
        if (!answer.started()) {
            source.sendFailure(answer.message());
            return 0;
        }
        source.sendSuccess(answer::message, true);
        return 1;
    }

    private static int sites(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<BuilderSite> sites = BuilderSites.all(source.getLevel());
        source.sendSuccess(() -> Component.translatable(KEY + "sites", sites.size()), false);
        for (BuilderSite site : sites) {
            BlockPos origin = site.origin();
            Component name = site.isRepair()
                    ? Component.translatable(KEY + "site.repair", site.repairPositions().size())
                    : Component.literal(site.template().toString());
            source.sendSuccess(() -> Component.translatable(KEY + "site", site.id(), name,
                    origin.getX(), origin.getY(), origin.getZ(),
                    Component.translatable(KEY + "site.phase." + site.phase().serializedName()), site.appliedSteps(),
                    site.totalSteps(), Component.translatable(KEY + "site.status." + site.status().serializedName()),
                    site.builderCount()), false);
            if (site.pendingSteps() >= 1.0) {
                source.sendSuccess(() -> Component.translatable(KEY + "site.pending",
                        (int) site.pendingSteps()), false);
            }
            if (!site.missingMaterials().isEmpty()) {
                source.sendSuccess(() -> Component.translatable(KEY + "site.missing",
                        String.join(", ", site.missingMaterials())), false);
            }
        }
        return sites.size();
    }

    private static int capture(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos from = BlockPosArgument.getLoadedBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(context, "to");
        BoundingBox box = BoundingBox.fromCorners(from, to);
        long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        if (volume > MAX_CAPTURE) {
            source.sendFailure(Component.translatable(KEY + "too_large", volume, MAX_CAPTURE));
            return 0;
        }
        ServerLevel level = source.getLevel();
        Target target = ConstructionTargets.fromLevel(level, from, to);
        capture = new Capture(level.dimension(), box, target);
        source.sendSuccess(() -> Component.translatable(KEY + "captured", volume, box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ()), true);
        return (int) volume;
    }

    private static int restore(CommandContext<CommandSourceStack> context, int stepsPerTick) {
        CommandSourceStack source = context.getSource();
        Capture saved = capture;
        if (saved == null) {
            source.sendFailure(Component.translatable(KEY + "no_capture"));
            return 0;
        }
        ServerLevel level = source.getServer().getLevel(saved.dimension());
        if (level == null) {
            source.sendFailure(Component.translatable(KEY + "no_capture"));
            return 0;
        }
        if (!ConstructionPlacement.isAreaReady(level, saved.box())) {
            source.sendFailure(Component.translatable(NOT_LOADED));
            return 0;
        }
        return ConstructionPlacement.start(source, level, saved.target(), stepsPerTick);
    }

    private static int openMenu(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ConstructionPlacement.openMenu(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int place(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ConstructionPlacement.sendPreview(context.getSource().getPlayerOrException(),
                ResourceLocationArgument.getId(context, "template"));
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int stopped = ConstructionRuns.stop(source.getLevel()) + BuilderSites.stop(source.getLevel());
        source.sendSuccess(() -> Component.translatable(KEY + "stopped", stopped), true);
        return stopped;
    }
}
