package io.github.nicolasgoyon.folkhold.events.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;
import io.github.nicolasgoyon.folkhold.events.earthquake.Earthquake;
import io.github.nicolasgoyon.folkhold.events.earthquake.EarthquakeData;
import io.github.nicolasgoyon.folkhold.events.earthquake.EarthquakeMath;
import io.github.nicolasgoyon.folkhold.events.earthquake.EarthquakeSettings;
import io.github.nicolasgoyon.folkhold.events.earthquake.Earthquakes;
import java.util.List;
import java.util.Locale;
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
 * </ul>
 */
public final class EventsCommands {
    /** The permission level of the commands: the operators. */
    public static final int PERMISSION_LEVEL = 2;
    private static final String KEY = "commands.folkhold_events.earthquake.";

    private EventsCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("folkhold")
                .then(Commands.literal("earthquake")
                        .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                        .then(Commands.literal("start")
                                .executes(context -> start(context, sourcePos(context.getSource()),
                                        defaultRadius(), 1.0))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> start(context,
                                                BlockPosArgument.getLoadedBlockPos(context, "pos"), defaultRadius(),
                                                1.0))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 64))
                                                .executes(context -> start(context,
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                        IntegerArgumentType.getInteger(context, "radius"), 1.0))
                                                .then(Commands.argument("strength",
                                                                DoubleArgumentType.doubleArg(0.1, 10.0))
                                                        .executes(context -> start(context,
                                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                IntegerArgumentType.getInteger(context, "radius"),
                                                                DoubleArgumentType.getDouble(context,
                                                                        "strength")))))))
                        .then(Commands.literal("stop").executes(EventsCommands::stop))
                        .then(Commands.literal("status").executes(EventsCommands::status))));
    }

    private static int defaultRadius() {
        return EventsConfig.EARTHQUAKE_RADIUS.get();
    }

    /** The position of a source. The arguments with a position check the load of their chunk themselves. */
    private static BlockPos sourcePos(CommandSourceStack source) throws CommandSyntaxException {
        BlockPos pos = BlockPos.containing(source.getPosition());
        if (!source.getLevel().isLoaded(pos)) {
            throw BlockPosArgument.ERROR_NOT_LOADED.create();
        }
        return pos;
    }

    private static int start(CommandContext<CommandSourceStack> context, BlockPos pos, int radius, double strength) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        if (Earthquakes.start(level, pos, EarthquakeSettings.fromConfig(radius, strength)).isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "refused"));
            return 0;
        }
        String shownStrength = String.format(Locale.ROOT, "%.2f", strength);
        source.sendSuccess(() -> Component.translatable(KEY + "started", pos.getX(), pos.getY(), pos.getZ(), radius,
                shownStrength), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int stopped = Earthquakes.stop(source.getLevel());
        source.sendSuccess(() -> Component.translatable(KEY + "stopped", stopped), true);
        return stopped;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        List<Earthquake> active = Earthquakes.active(level);
        if (active.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "status.none"), false);
        }
        for (Earthquake earthquake : active) {
            BlockPos center = earthquake.center();
            Component phase = Component.translatable(KEY + "phase." + earthquake.phase().name()
                    .toLowerCase(Locale.ROOT));
            source.sendSuccess(() -> Component.translatable(KEY + "status.entry", center.getX(), center.getY(),
                    center.getZ(), phase, earthquake.changedBlocks()), false);
        }
        long next = EarthquakeData.get(level).nextTime();
        if (!Earthquakes.hasNaturalEarthquakes(level) || next < 0L) {
            source.sendSuccess(() -> Component.translatable(KEY + "status.off"), false);
        } else {
            double days = Math.max(0L, next - level.getGameTime()) / (double) EarthquakeMath.TICKS_PER_DAY;
            String shownDays = String.format(Locale.ROOT, "%.1f", days);
            source.sendSuccess(() -> Component.translatable(KEY + "status.next", shownDays), false);
        }
        return active.size();
    }
}
