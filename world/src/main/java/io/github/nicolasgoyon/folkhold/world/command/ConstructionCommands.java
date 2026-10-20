package io.github.nicolasgoyon.folkhold.world.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.nicolasgoyon.folkhold.core.sim.construction.ConstructionPlanner;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Plan;
import io.github.nicolasgoyon.folkhold.core.sim.construction.PlanWarning;
import io.github.nicolasgoyon.folkhold.core.sim.construction.StepType;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Target;
import io.github.nicolasgoyon.folkhold.world.admin.AdminActions;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionRun;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionRuns;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import java.util.Locale;
import java.util.Optional;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Commands of the construction plans (permission level 2):
 * <ul>
 *   <li>{@code /folkhold construction build <template> [stepsPerTick]}: builds a structure template step by step. The
 *       origin of the template is the position of the source.</li>
 *   <li>{@code /folkhold construction capture <from> <to>}: keeps the blocks of a box, as the target of
 *       {@code restore}.</li>
 *   <li>{@code /folkhold construction restore [stepsPerTick]}: puts back the blocks of the captured box, step by
 *       step.</li>
 *   <li>{@code /folkhold construction stop}: stops the constructions in the level of the source.</li>
 * </ul>
 * A command that starts a construction shows the plan: its steps and its warnings. The construction shows a message
 * at its end.
 */
public final class ConstructionCommands {
    private static final String KEY = "commands.folkhold_world.construction.";
    /** A capture has this number of blocks at most, as the vanilla command {@code /fill}. */
    private static final int MAX_CAPTURE = 32768;
    private static final int DEFAULT_STEPS_PER_TICK = 4;
    private static final int MAX_STEPS_PER_TICK = 1000;
    /** A command shows this number of warnings at most. */
    private static final int SHOWN_WARNINGS = 8;

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
                                        .then(stepsPerTick()
                                                .executes(context -> build(context, stepsPerTick(context))))))
                        .then(Commands.literal("capture")
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(ConstructionCommands::capture))))
                        .then(Commands.literal("restore")
                                .executes(context -> restore(context, DEFAULT_STEPS_PER_TICK))
                                .then(stepsPerTick().executes(context -> restore(context, stepsPerTick(context)))))
                        .then(Commands.literal("stop").executes(ConstructionCommands::stop))));
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        capture = null;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> stepsPerTick() {
        return Commands.argument("stepsPerTick", IntegerArgumentType.integer(1, MAX_STEPS_PER_TICK));
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
        Target target = ConstructionTargets.fromTemplate(level, template.get(), origin, new StructurePlaceSettings());
        return start(source, level, target, stepsPerTick);
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
        return start(source, level, saved.target(), stepsPerTick);
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int stopped = ConstructionRuns.stop(source.getLevel());
        source.sendSuccess(() -> Component.translatable(KEY + "stopped", stopped), true);
        return stopped;
    }

    /** Makes the plan of a target, shows it, and starts its run. */
    private static int start(CommandSourceStack source, ServerLevel level, Target target, int stepsPerTick) {
        Plan plan = ConstructionPlanner.plan(target, ConstructionTargets.view(level),
                WorldConfig.CONSTRUCTION_REACH.get());
        source.sendSuccess(() -> Component.translatable(KEY + "plan", plan.steps().size(), plan.count(StepType.REMOVE),
                plan.count(StepType.PLACE), plan.count(StepType.PLACE_SCAFFOLDING), plan.count(StepType.ADD_FLUID),
                plan.warnings().size()), true);
        for (PlanWarning warning : plan.warnings().subList(0, Math.min(SHOWN_WARNINGS, plan.warnings().size()))) {
            source.sendSuccess(() -> Component.translatable(KEY + "warning."
                    + warning.problem().name().toLowerCase(Locale.ROOT), warning.pos().x(), warning.pos().y(),
                    warning.pos().z()), false);
        }
        if (plan.warnings().size() > SHOWN_WARNINGS) {
            source.sendSuccess(() -> Component.translatable(KEY + "more_warnings",
                    plan.warnings().size() - SHOWN_WARNINGS), false);
        }
        ConstructionRun run = new ConstructionRun(level, plan, stepsPerTick);
        ConstructionRuns.start(run, () -> source.sendSuccess(() -> Component.translatable(KEY + "finished",
                run.total()), true));
        return plan.steps().size();
    }
}
