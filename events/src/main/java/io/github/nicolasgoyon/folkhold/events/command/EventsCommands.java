package io.github.nicolasgoyon.folkhold.events.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.nicolasgoyon.folkhold.events.common.EventMath;
import io.github.nicolasgoyon.folkhold.events.common.EventSchedule;
import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;
import io.github.nicolasgoyon.folkhold.events.earthquake.Earthquake;
import io.github.nicolasgoyon.folkhold.events.earthquake.EarthquakeSettings;
import io.github.nicolasgoyon.folkhold.events.earthquake.Earthquakes;
import io.github.nicolasgoyon.folkhold.events.storm.Storm;
import io.github.nicolasgoyon.folkhold.events.storm.StormMath;
import io.github.nicolasgoyon.folkhold.events.storm.StormSettings;
import io.github.nicolasgoyon.folkhold.events.storm.Storms;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Commands of the events mod, for the operators (permission level {@value #PERMISSION_LEVEL}):
 * <ul>
 *   <li>{@code /folkhold earthquake start [pos] [radius] [strength]}: starts an earthquake. The default position is
 *       the position of the source. The default radius is the radius of the config. The default strength is 1.</li>
 *   <li>{@code /folkhold earthquake stop}: stops the earthquakes of the level of the source.</li>
 *   <li>{@code /folkhold earthquake status}: the active earthquakes and the time of the next natural earthquake.</li>
 *   <li>{@code /folkhold storm start [pos] [radius] [strength]}: starts a storm with a random wind direction, with
 *       the same defaults.</li>
 *   <li>{@code /folkhold storm stop}: stops the storms of the level of the source.</li>
 *   <li>{@code /folkhold storm status}: the active storms and the time of the next natural storm.</li>
 * </ul>
 */
public final class EventsCommands {
    /** The permission level of the commands: the operators. */
    public static final int PERMISSION_LEVEL = 2;
    private static final String EARTHQUAKE_KEY = "commands.folkhold_events.earthquake.";
    private static final String STORM_KEY = "commands.folkhold_events.storm.";

    private EventsCommands() {
    }

    /** Starts an event at a position, with a radius and a strength. */
    @FunctionalInterface
    private interface Starter {
        int start(CommandContext<CommandSourceStack> context, BlockPos pos, int radius, double strength);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("folkhold")
                .then(Commands.literal("earthquake")
                        .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                        .then(startCommand(EventsConfig.EARTHQUAKE_RADIUS::get, 4, 64,
                                EventsCommands::startEarthquake))
                        .then(Commands.literal("stop").executes(EventsCommands::stopEarthquakes))
                        .then(Commands.literal("status").executes(EventsCommands::earthquakeStatus)))
                .then(Commands.literal("storm")
                        .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                        .then(startCommand(EventsConfig.STORM_RADIUS::get, 8, 96, EventsCommands::startStorm))
                        .then(Commands.literal("stop").executes(EventsCommands::stopStorms))
                        .then(Commands.literal("status").executes(EventsCommands::stormStatus))));
    }

    /** The {@code start [pos] [radius] [strength]} branch, with a default strength of 1. */
    private static LiteralArgumentBuilder<CommandSourceStack> startCommand(IntSupplier defaultRadius, int minRadius,
            int maxRadius, Starter starter) {
        return Commands.literal("start")
                .executes(context -> starter.start(context, sourcePos(context.getSource()),
                        defaultRadius.getAsInt(), 1.0))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> starter.start(context, BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                defaultRadius.getAsInt(), 1.0))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(minRadius, maxRadius))
                                .executes(context -> starter.start(context,
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                        IntegerArgumentType.getInteger(context, "radius"), 1.0))
                                .then(Commands.argument("strength", DoubleArgumentType.doubleArg(0.1, 10.0))
                                        .executes(context -> starter.start(context,
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                IntegerArgumentType.getInteger(context, "radius"),
                                                DoubleArgumentType.getDouble(context, "strength"))))));
    }

    /** The position of a source. The arguments with a position check the load of their chunk themselves. */
    private static BlockPos sourcePos(CommandSourceStack source) throws CommandSyntaxException {
        BlockPos pos = BlockPos.containing(source.getPosition());
        if (!source.getLevel().isLoaded(pos)) {
            throw BlockPosArgument.ERROR_NOT_LOADED.create();
        }
        return pos;
    }

    private static String formatted(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int startEarthquake(CommandContext<CommandSourceStack> context, BlockPos pos, int radius,
            double strength) {
        CommandSourceStack source = context.getSource();
        if (Earthquakes.start(source.getLevel(), pos, EarthquakeSettings.fromConfig(radius, strength)).isEmpty()) {
            source.sendFailure(Component.translatable(EARTHQUAKE_KEY + "refused"));
            return 0;
        }
        String shownStrength = formatted(strength);
        source.sendSuccess(() -> Component.translatable(EARTHQUAKE_KEY + "started", pos.getX(), pos.getY(),
                pos.getZ(), radius, shownStrength), true);
        return 1;
    }

    private static int stopEarthquakes(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int stopped = Earthquakes.stop(source.getLevel());
        source.sendSuccess(() -> Component.translatable(EARTHQUAKE_KEY + "stopped", stopped), true);
        return stopped;
    }

    private static int earthquakeStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        List<Earthquake> active = Earthquakes.active(level);
        if (active.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(EARTHQUAKE_KEY + "status.none"), false);
        }
        for (Earthquake earthquake : active) {
            BlockPos center = earthquake.center();
            Component phase = Component.translatable(EARTHQUAKE_KEY + "phase." + earthquake.phase().name()
                    .toLowerCase(Locale.ROOT));
            source.sendSuccess(() -> Component.translatable(EARTHQUAKE_KEY + "status.entry", center.getX(),
                    center.getY(), center.getZ(), phase, earthquake.changedBlocks()), false);
        }
        sendNext(source, EARTHQUAKE_KEY, Earthquakes.hasNaturalEarthquakes(level), Earthquakes.schedule(level));
        return active.size();
    }

    private static int startStorm(CommandContext<CommandSourceStack> context, BlockPos pos, int radius,
            double strength) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        double windAngle = level.getRandom().nextDouble() * 360.0;
        if (Storms.start(level, pos, StormSettings.fromConfig(radius, strength, windAngle)).isEmpty()) {
            source.sendFailure(Component.translatable(STORM_KEY + "refused"));
            return 0;
        }
        String shownStrength = formatted(strength);
        Component wind = windName(windAngle);
        source.sendSuccess(() -> Component.translatable(STORM_KEY + "started", pos.getX(), pos.getY(), pos.getZ(),
                radius, shownStrength, wind), true);
        return 1;
    }

    private static int stopStorms(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int stopped = Storms.stop(source.getLevel());
        source.sendSuccess(() -> Component.translatable(STORM_KEY + "stopped", stopped), true);
        return stopped;
    }

    private static int stormStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        List<Storm> active = Storms.active(level);
        if (active.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(STORM_KEY + "status.none"), false);
        }
        for (Storm storm : active) {
            BlockPos center = storm.center();
            Component phase = Component.translatable(STORM_KEY + "phase." + storm.phase().name()
                    .toLowerCase(Locale.ROOT));
            Component wind = windName(storm.settings().windAngle());
            source.sendSuccess(() -> Component.translatable(STORM_KEY + "status.entry", center.getX(), center.getY(),
                    center.getZ(), phase, wind, storm.changedBlocks(), storm.plannedBlocks()), false);
        }
        sendNext(source, STORM_KEY, Storms.hasNaturalStorms(level), Storms.schedule(level));
        return active.size();
    }

    private static Component windName(double windAngle) {
        return Component.translatable(STORM_KEY + "wind." + StormMath.compass(windAngle));
    }

    /** Sends the time to the next natural event, or the message for no natural event. */
    private static void sendNext(CommandSourceStack source, String key, boolean natural, EventSchedule schedule) {
        long next = schedule.nextTime();
        if (!natural || next < 0L) {
            source.sendSuccess(() -> Component.translatable(key + "status.off"), false);
        } else {
            double days = Math.max(0L, next - source.getLevel().getGameTime()) / (double) EventMath.TICKS_PER_DAY;
            String shownDays = String.format(Locale.ROOT, "%.1f", days);
            source.sendSuccess(() -> Component.translatable(key + "status.next", shownDays), false);
        }
    }
}
