package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiStrings;
import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomyState;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.PlayerReputation;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationSettings;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.trade.TradeMatcher;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.faction.Cultures;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.group.VillageTrade;
import io.github.nicolasgoyon.folkhold.world.network.VillageHeartPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageHeartPayload.Service;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * The content of the village heart screen, for one player.
 *
 * <p>The player part comes first:
 * <ul>
 *   <li>the welcome line, for the reputation tier of the player;</li>
 *   <li>the reputation: tier, effective value, village value and faction value with their weights;</li>
 *   <li>what the player can do here: quests (with a link to the quest board), trade (discount, merchants, the items
 *       that the village looks for), shelter at night, inn;</li>
 *   <li>an advice: what not to do, or how to improve a bad reputation.</li>
 * </ul>
 *
 * <p>The village part is in sections:
 * <ul>
 *   <li><b>village</b>: faction, culture, level and tier, experience (bar), alert state, position;</li>
 *   <li><b>garrison</b>: for each role, the units alive and dead, and the configured count (segments);</li>
 *   <li><b>economy</b>: population (bar), jobs, food, treasury of the faction, and the main stocks with their
 *       price;</li>
 *   <li><b>territory</b>: number of claimed territory chunks.</li>
 * </ul>
 * The services, the economy and the territory need the settlement of the village.
 */
public final class VillageInfo {
    private static final String KEY = "screen.folkhold_world.village_heart.";
    /** The economy section shows the stocks with the largest quantities. */
    private static final int STOCK_ROWS = 6;
    /** The trade service shows the largest needs of the village. */
    private static final int NEED_ITEMS = 3;
    /** A village of this level or more is a town: it can have an inn. */
    private static final int TOWN_LEVEL = 9;
    /** Colors of the texts on parchment. */
    private static final int INK_GOOD = 0x3F6B28;
    private static final int INK_BAD = 0x8E2F22;
    private static final int INK_MUTED = 0x6B5A43;
    private static final int INK_FAINT = 0x7A6A50;
    /** Color of a bad value on wood. */
    private static final int TEXT_BAD = 0xE8846F;

    private VillageInfo() {
    }

    /**
     * Builds the screen of a village heart for a player.
     *
     * @param heart  the village heart
     * @param player the player
     * @return the payload
     */
    public static VillageHeartPayload build(VillageHeartBlockEntity heart, ServerPlayer player) {
        MinecraftServer server = player.server;
        Optional<FactionSpec> faction = FactionVisuals.faction(server, heart.getFactionId());
        Optional<Settlement> settlement = Settlements.of(server, heart);
        String name = heart.getVillageName();
        Component title = name != null ? Component.literal(name) : Component.translatable(KEY + "unnamed");
        Component culture = faction.flatMap(spec -> Cultures.byId(spec.cultureId()))
                .<Component>map(found -> Component.translatable(found.translationKey()))
                .orElse(Component.empty());

        ReputationTier tier = ReputationTier.NEUTRAL;
        int effective = 0;
        int villageValue = 0;
        int factionValue = 0;
        ReputationSettings settings = Reputations.settings();
        if (faction.isPresent()) {
            PlayerReputation reputation = ReputationData.get(server).get(player.getUUID());
            String villageId = heart.getVillageId().toString();
            effective = reputation.effective(faction.get(), villageId, settings);
            villageValue = reputation.village(faction.get(), villageId);
            factionValue = reputation.faction(faction.get());
            tier = ReputationTier.of(effective);
        }

        List<Service> services = new ArrayList<>();
        Component welcome = Component.empty();
        Component advice = Component.empty();
        boolean adviceDanger = false;
        if (faction.isPresent() && settlement.isPresent()) {
            welcome = Component.translatable(KEY + "welcome." + tier.serializedName());
            services.add(questService(server, player, settlement.get(), tier));
            services.add(tradeService(settlement.get(), tier));
            services.add(shelterService(tier));
            services.add(innService(settlement.get()));
            if (tier.isHostile()) {
                advice = Component.translatable(KEY + "advice.improve", FactionVisuals.name(faction.get()));
            } else {
                advice = Component.translatable(KEY + "advice.avoid");
                adviceDanger = true;
            }
        }

        List<Section> sections = new ArrayList<>();
        sections.add(villageSection(heart, faction, settlement, culture));
        sections.add(garrisonSection(heart));
        settlement.ifPresent(found -> {
            sections.add(economySection(server, found));
            sections.add(territorySection(server, found));
        });
        int level = settlement.map(Settlement::level).orElse(0);
        return new VillageHeartPayload(settlement.map(Settlement::id).orElse(""), title,
                faction.<Component>map(FactionVisuals::name).orElse(Component.empty()), culture,
                faction.map(FactionSpec::color).orElse(-1), level,
                level > 0 ? SettlementLevels.tierName(level) : Component.empty(),
                heart.getAlertState().serializedName(), tier.serializedName(), effective,
                villageValue, factionValue, (int) Math.round(settings.villageWeight() * 100.0), welcome, services,
                advice, adviceDanger, sections);
    }

    // ------------------------------------------------------------------ services

    private static Service questService(MinecraftServer server, ServerPlayer player, Settlement settlement,
            ReputationTier tier) {
        Component name = Component.translatable(KEY + "service.quests");
        if (tier.isHostile()) {
            return new Service(name, refused(Component.translatable(KEY + "quests.refused")),
                    VillageHeartPayload.REFUSED, Component.empty(), List.of(), VillageHeartPayload.ACTION_NONE);
        }
        List<Quest> offers = QuestData.get(server).quests().offers(settlement.id());
        long forFriends = offers.stream().filter(quest -> quest.minTier().isFriendly()).count();
        int active = QuestData.get(server).quests().active(player.getUUID()).size();
        int max = WorldConfig.QUEST_MAX_ACTIVE.get();
        Component state = offers.isEmpty()
                ? Component.translatable(KEY + "quests.none").withColor(INK_MUTED)
                : available(Component.translatable(KEY + "quests.offers", offers.size()));
        Component details = forFriends > 0
                ? Component.translatable(KEY + "quests.details_friends", forFriends, active, max)
                : Component.translatable(KEY + "quests.details", active, max);
        return new Service(name, state, offers.isEmpty() ? VillageHeartPayload.LIMITED : VillageHeartPayload.AVAILABLE,
                details, List.of(), VillageHeartPayload.ACTION_QUEST_BOARD);
    }

    private static Service tradeService(Settlement settlement, ReputationTier tier) {
        Component name = Component.translatable(KEY + "service.trade");
        if (tier.isHostile()) {
            return new Service(name, refused(Component.translatable(KEY + "trade.refused",
                    FactionVisuals.tierName(ReputationTier.NEUTRAL))),
                    VillageHeartPayload.REFUSED, Component.empty(), List.of(), VillageHeartPayload.ACTION_NONE);
        }
        double factor = Merchants.priceFactor(tier);
        Component state = factor < 1.0
                ? available(Component.translatable(KEY + "trade.discount", Merchants.priceText(factor)))
                : available(Component.translatable(KEY + "trade.normal"));
        Component details = Component.translatable(KEY + "trade.merchants", Merchants.merchantCount(settlement));
        List<String> needs = List.of();
        if (EconomyData.isActive()) {
            EconomyState economy = settlement.economy();
            Map<String, Integer> found = TradeMatcher.needs(economy.stock(), economy.population(),
                    EconomyData.catalog(), VillageTrade.usedItems(settlement, EconomyData.catalog()));
            needs = found.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(NEED_ITEMS)
                    .map(Map.Entry::getKey)
                    .toList();
        }
        return new Service(name, state, VillageHeartPayload.AVAILABLE, details, needs,
                VillageHeartPayload.ACTION_NONE);
    }

    private static Service shelterService(ReputationTier tier) {
        Component name = Component.translatable(KEY + "service.shelter");
        if (tier.isFriendly()) {
            return new Service(name, available(Component.translatable(KEY + "shelter.open")),
                    VillageHeartPayload.AVAILABLE, Component.empty(), List.of(), VillageHeartPayload.ACTION_NONE);
        }
        if (tier.isHostile()) {
            return new Service(name, refused(Component.translatable(KEY + "shelter.closed")),
                    VillageHeartPayload.REFUSED, Component.empty(), List.of(), VillageHeartPayload.ACTION_NONE);
        }
        return new Service(name, Component.literal("– ").append(Component.translatable(KEY + "shelter.night"))
                .withColor(INK_MUTED), VillageHeartPayload.LIMITED, Component.empty(), List.of(),
                VillageHeartPayload.ACTION_NONE);
    }

    private static Service innService(Settlement settlement) {
        Component name = Component.translatable(KEY + "service.inn");
        Component state = settlement.level() < TOWN_LEVEL
                ? Component.literal("✕ ").append(Component.translatable(KEY + "inn.town",
                        SettlementLevels.tierName(TOWN_LEVEL), TOWN_LEVEL))
                : Component.literal("– ").append(Component.translatable(KEY + "inn.soon"));
        return new Service(name, state.copy().withColor(INK_FAINT), VillageHeartPayload.LATER, Component.empty(),
                List.of(), VillageHeartPayload.ACTION_NONE);
    }

    private static MutableComponent available(Component text) {
        return Component.literal("✓ ").append(text).withColor(INK_GOOD);
    }

    private static MutableComponent refused(Component text) {
        return Component.literal("✕ ").append(text).withColor(INK_BAD);
    }

    // ------------------------------------------------------------------ sections

    private static Section villageSection(VillageHeartBlockEntity heart, Optional<FactionSpec> faction,
            Optional<Settlement> settlement, Component culture) {
        List<Row> rows = new ArrayList<>();
        rows.add(row("faction", faction.<Component>map(FactionVisuals::name)
                .orElse(Component.translatable(KEY + "none"))));
        if (!culture.getString().isEmpty()) {
            rows.add(row("culture", culture));
        }
        settlement.ifPresent(found -> {
            rows.add(row("level", Component.translatable(KEY + "level_value", found.level(),
                    SettlementLevels.tierName(found.level()))));
            int needed = LevelTables.get().traits(found.level()).experience();
            if (found.level() >= LevelTables.get().maxLevel()) {
                rows.add(row("experience", Component.translatable(KEY + "experience_max")));
            } else {
                rows.add(Row.bar(Component.translatable(KEY + "experience"),
                        Component.translatable(KEY + "value", found.experience(), needed), found.experience(),
                        needed));
            }
        });
        rows.add(row("state", Component.translatable(heart.getAlertState().translationKey())));
        rows.add(row("position", Component.translatable(KEY + "position_value",
                heart.getBlockPos().getX(), heart.getBlockPos().getZ())));
        return new Section(Component.translatable(KEY + "village"), rows);
    }

    private static Section garrisonSection(VillageHeartBlockEntity heart) {
        List<Row> rows = new ArrayList<>();
        for (GarrisonRole role : GarrisonRole.values()) {
            int alive = (int) heart.getGarrison().entries().stream()
                    .filter(entry -> entry.role() == role && !entry.isDead()).count();
            int dead = (int) heart.getGarrison().entries().stream()
                    .filter(entry -> entry.role() == role && entry.isDead()).count();
            int configured = role.configuredCount();
            MutableComponent value = Component.translatable(KEY + "units_alive", alive, configured);
            if (dead > 0) {
                value.append(UiStrings.separator())
                        .append(Component.translatable(KEY + "units_dead", dead).withColor(TEXT_BAD));
            }
            rows.add(Row.segments(role.entityType().getDescription(), value, Math.min(alive, configured),
                    Math.max(configured, alive + dead), Math.min(dead, Math.max(0, configured - alive))));
        }
        return new Section(Component.translatable(KEY + "garrison"), rows);
    }

    private static Section economySection(MinecraftServer server, Settlement settlement) {
        EconomyState economy = settlement.economy();
        List<Row> rows = new ArrayList<>();
        int maxPopulation = LevelTables.get().traits(settlement.level()).maxPopulation();
        rows.add(Row.bar(Component.translatable(KEY + "population"),
                Component.translatable(KEY + "value", economy.population(), maxPopulation), economy.population(),
                maxPopulation));
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
                    rows.add(new Row(itemName(entry.getKey()), Component.translatable(KEY + "stock_value",
                            entry.getValue(), Merchants.priceText(price))));
                });
        return new Section(Component.translatable(KEY + "economy"), rows);
    }

    private static Section territorySection(MinecraftServer server, Settlement settlement) {
        int chunks = SettlementData.get(server).claims().chunkCount(settlement.id());
        return new Section(Component.translatable(KEY + "territory"),
                List.of(row("claimed_chunks", Component.literal(String.valueOf(chunks)))));
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
