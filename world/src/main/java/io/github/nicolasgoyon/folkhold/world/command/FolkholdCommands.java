package io.github.nicolasgoyon.folkhold.world.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.faction.Relations;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.PlayerReputation;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.faction.Cultures;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Commands of the world mod:
 * <ul>
 *   <li>{@code /folkhold factions}: the factions of the world and their relations.</li>
 *   <li>{@code /folkhold reputation [player]}: the reputation of a player with each faction.</li>
 *   <li>{@code /folkhold reputation set <player> <faction> <value>}: sets a reputation (operators only).</li>
 * </ul>
 *
 * <p>{@link SettlementCommands} adds the commands of the abstract simulation. {@link GroupCommands} adds the
 * commands of the caravans and the quests.
 */
public final class FolkholdCommands {
    private static final String KEY = "commands.folkhold_world.";
    private static final int OPERATOR_LEVEL = 2;

    private FolkholdCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
        SettlementCommands.register(event.getDispatcher());
        GroupCommands.register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("folkhold")
                .then(Commands.literal("factions").executes(FolkholdCommands::listFactions))
                .then(Commands.literal("reputation")
                        .executes(context -> showReputation(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> showReputation(context,
                                        EntityArgument.getPlayer(context, "player"))))
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(OPERATOR_LEVEL))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("faction", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        FactionData.get(context.getSource().getServer()).factions()
                                                                .stream().map(FactionSpec::id), builder))
                                                .then(Commands.argument("value",
                                                                IntegerArgumentType.integer(PlayerReputation.MIN,
                                                                        PlayerReputation.MAX))
                                                        .executes(FolkholdCommands::setReputation)))))));
    }

    private static MutableComponent factionName(FactionSpec faction) {
        return Component.literal(faction.name()).withStyle(style -> style.withColor(TextColor.fromRgb(faction.color())));
    }

    private static Component cultureName(FactionSpec faction) {
        return Cultures.byId(faction.cultureId())
                .<Component>map(culture -> Component.translatable(culture.translationKey()))
                .orElse(Component.literal(faction.cultureId()));
    }

    private static Component tierName(ReputationTier tier) {
        return Component.translatable("folkhold_world.reputation_tier." + tier.serializedName());
    }

    private static int listFactions(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        FactionData data = FactionData.get(source.getServer());
        List<FactionSpec> factions = data.factions();
        source.sendSuccess(() -> Component.translatable(KEY + "factions.header", factions.size()), false);
        for (FactionSpec faction : factions) {
            Component attitude = Component.translatable(
                    "folkhold_world.attitude." + faction.attitude().name().toLowerCase(Locale.ROOT));
            source.sendSuccess(() -> Component.translatable(KEY + "factions.entry", factionName(faction),
                    faction.id(), cultureName(faction), attitude, faction.centerX(), faction.centerZ()), false);
        }
        Relations relations = data.relations();
        source.sendSuccess(() -> Component.translatable(KEY + "factions.relations"), false);
        for (int i = 0; i < factions.size(); i++) {
            for (int j = i + 1; j < factions.size(); j++) {
                FactionSpec a = factions.get(i);
                FactionSpec b = factions.get(j);
                int value = relations.get(a.id(), b.id());
                Component state = Component.translatable("folkhold_world.relation_state."
                        + relations.state(a.id(), b.id()).name().toLowerCase(Locale.ROOT));
                source.sendSuccess(() -> Component.translatable(KEY + "factions.relation",
                        factionName(a), factionName(b), value, state), false);
            }
        }
        return factions.size();
    }

    private static int showReputation(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CommandSourceStack source = context.getSource();
        FactionData data = FactionData.get(source.getServer());
        PlayerReputation reputation = ReputationData.get(source.getServer()).get(player.getUUID());
        source.sendSuccess(() -> Component.translatable(KEY + "reputation.header", player.getDisplayName()), false);
        for (FactionSpec faction : data.factions()) {
            int value = reputation.faction(faction);
            source.sendSuccess(() -> Component.translatable(KEY + "reputation.entry", factionName(faction), value,
                    tierName(ReputationTier.of(value))), false);
        }
        return data.factions().size();
    }

    private static int setReputation(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String factionId = StringArgumentType.getString(context, "faction");
        int value = IntegerArgumentType.getInteger(context, "value");
        Optional<FactionSpec> faction = FactionData.get(source.getServer()).byId(factionId);
        if (faction.isEmpty()) {
            source.sendFailure(Component.translatable(KEY + "unknown_faction", factionId));
            return 0;
        }
        ReputationData data = ReputationData.get(source.getServer());
        data.get(player.getUUID()).setFaction(factionId, value);
        data.setDirty();
        source.sendSuccess(() -> Component.translatable(KEY + "reputation.set", player.getDisplayName(),
                factionName(faction.get()), value, tierName(ReputationTier.of(value))), true);
        return 1;
    }
}
