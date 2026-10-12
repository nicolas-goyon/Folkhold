package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomyState;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.PlayerReputation;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationSettings;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.faction.Cultures;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * The content of the village heart screen, for one player. Sections:
 * <ul>
 *   <li><b>village</b>: name, faction, culture, level and tier, experience, alert state, position;</li>
 *   <li><b>garrison</b>: for each role, the units alive and dead, and the configured count;</li>
 *   <li><b>reputation</b>: tier, effective value, village value and faction value of the player;</li>
 *   <li><b>economy</b>: population, jobs, food, treasury of the faction, and the main stocks with their price;</li>
 *   <li><b>territory</b>: number of claimed territory cells.</li>
 * </ul>
 * The economy and territory sections need the settlement of the village.
 */
public final class VillageInfo {
    private static final String KEY = "screen.folkhold_world.village_heart.";
    /** The economy section shows the stocks with the largest quantities. */
    private static final int STOCK_ROWS = 6;

    private VillageInfo() {
    }

    public static VillageInfoPayload build(VillageHeartBlockEntity heart, ServerPlayer player) {
        MinecraftServer server = player.server;
        Optional<FactionSpec> faction = FactionVisuals.faction(server, heart.getFactionId());
        String name = heart.getVillageName();
        Component title = name != null ? Component.literal(name) : Component.translatable(KEY + "unnamed");

        List<Section> sections = new ArrayList<>();
        sections.add(villageSection(server, heart, faction));
        sections.add(garrisonSection(heart));
        faction.ifPresent(spec -> sections.add(reputationSection(server, player, heart, spec)));
        Settlements.of(server, heart).ifPresent(settlement -> {
            sections.add(economySection(server, settlement));
            sections.add(territorySection(server, settlement));
        });
        return new VillageInfoPayload(title, sections);
    }

    private static Section villageSection(MinecraftServer server, VillageHeartBlockEntity heart,
            Optional<FactionSpec> faction) {
        List<Row> rows = new ArrayList<>();
        rows.add(row("faction", faction.<Component>map(FactionVisuals::name)
                .orElse(Component.translatable(KEY + "none"))));
        faction.flatMap(spec -> Cultures.byId(spec.cultureId()))
                .ifPresent(culture -> rows.add(row("culture", Component.translatable(culture.translationKey()))));
        Settlements.of(server, heart).ifPresent(settlement -> {
            rows.add(row("level", Component.translatable(KEY + "level_value", settlement.level(),
                    SettlementLevels.tierName(settlement.level()))));
            int needed = LevelTables.get().traits(settlement.level()).experience();
            rows.add(row("experience", settlement.level() >= LevelTables.get().maxLevel()
                    ? Component.translatable(KEY + "experience_max")
                    : Component.translatable(KEY + "value", settlement.experience(), needed)));
        });
        rows.add(row("state", Component.translatable(heart.getAlertState().translationKey())));
        rows.add(row("position", Component.translatable(KEY + "position_value",
                heart.getBlockPos().getX(), heart.getBlockPos().getZ())));
        return new Section(Component.translatable(KEY + "village"), rows);
    }

    private static Section garrisonSection(VillageHeartBlockEntity heart) {
        List<Row> rows = new ArrayList<>();
        for (GarrisonRole role : GarrisonRole.values()) {
            long alive = heart.getGarrison().entries().stream()
                    .filter(entry -> entry.role() == role && !entry.isDead()).count();
            long dead = heart.getGarrison().entries().stream()
                    .filter(entry -> entry.role() == role && entry.isDead()).count();
            rows.add(new Row(role.entityType().getDescription(),
                    Component.translatable(KEY + "units", alive, role.configuredCount(), dead)));
        }
        return new Section(Component.translatable(KEY + "garrison"), rows);
    }

    private static Section reputationSection(MinecraftServer server, ServerPlayer player,
            VillageHeartBlockEntity heart, FactionSpec faction) {
        PlayerReputation reputation = ReputationData.get(server).get(player.getUUID());
        ReputationSettings settings = Reputations.settings();
        String villageId = heart.getVillageId().toString();
        int effective = reputation.effective(faction, villageId, settings);
        int villagePercent = (int) Math.round(settings.villageWeight() * 100.0);
        List<Row> rows = new ArrayList<>();
        rows.add(row("tier", Component.translatable(
                "folkhold_world.reputation_tier." + ReputationTier.of(effective).serializedName())));
        rows.add(row("effective", Component.translatable(KEY + "value", effective, PlayerReputation.MAX)));
        rows.add(row("village_value", Component.translatable(KEY + "weighted_value",
                reputation.village(faction, villageId), villagePercent)));
        rows.add(row("faction_value", Component.translatable(KEY + "weighted_value",
                reputation.faction(faction), 100 - villagePercent)));
        return new Section(Component.translatable(KEY + "reputation"), rows);
    }

    private static Section economySection(MinecraftServer server, Settlement settlement) {
        EconomyState economy = settlement.economy();
        List<Row> rows = new ArrayList<>();
        rows.add(row("population", Component.translatable(KEY + "value", economy.population(),
                LevelTables.get().traits(settlement.level()).maxPopulation())));
        rows.add(row("jobs", Component.literal(String.valueOf(economy.jobs().size()))));
        rows.add(row("food", Component.translatable(KEY + "percent", Math.round(economy.foodCovered() * 100))));
        rows.add(row("treasury", Component.translatable(KEY + "emeralds",
                SettlementData.get(server).treasury(settlement.factionId()))));
        economy.stock().asMap().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(STOCK_ROWS)
                .forEach(entry -> {
                    ItemInfo info = EconomyData.catalog().info(entry.getKey());
                    double price = Prices.sellPrice(info, entry.getValue(), economy.population(),
                            Economies.settings());
                    Component name = itemName(entry.getKey());
                    rows.add(new Row(name, Component.translatable(KEY + "stock_value", entry.getValue(),
                            String.format(Locale.ROOT, "%.2f", price))));
                });
        return new Section(Component.translatable(KEY + "economy"), rows);
    }

    private static Section territorySection(MinecraftServer server, Settlement settlement) {
        int cells = SettlementData.get(server).claims().cellCount(settlement.id());
        return new Section(Component.translatable(KEY + "territory"),
                List.of(row("claimed_cells", Component.literal(String.valueOf(cells)))));
    }

    private static Component itemName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Component.literal(id)
                : BuiltInRegistries.ITEM.getOptional(location)
                        .<Component>map(Item::getDescription)
                        .orElse(Component.literal(id));
    }

    private static Row row(String labelKey, Component value) {
        return new Row(Component.translatable(KEY + labelKey), value);
    }
}
