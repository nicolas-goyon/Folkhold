package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.PlayerReputation;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationSettings;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.faction.Cultures;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The content of the village heart screen, for one player. Sections:
 * <ul>
 *   <li><b>village</b>: name, faction, culture, alert state, position;</li>
 *   <li><b>garrison</b>: for each role, the units alive and dead, and the configured count;</li>
 *   <li><b>reputation</b>: tier, effective value, village value and faction value of the player.</li>
 * </ul>
 */
public final class VillageInfo {
    private static final String KEY = "screen.folkhold_world.village_heart.";

    private VillageInfo() {
    }

    public static VillageInfoPayload build(VillageHeartBlockEntity heart, ServerPlayer player) {
        MinecraftServer server = player.server;
        Optional<FactionSpec> faction = FactionVisuals.faction(server, heart.getFactionId());
        String name = heart.getVillageName();
        Component title = name != null ? Component.literal(name) : Component.translatable(KEY + "unnamed");

        List<Section> sections = new ArrayList<>();
        sections.add(villageSection(heart, faction));
        sections.add(garrisonSection(heart));
        faction.ifPresent(spec -> sections.add(reputationSection(server, player, heart, spec)));
        return new VillageInfoPayload(title, sections);
    }

    private static Section villageSection(VillageHeartBlockEntity heart, Optional<FactionSpec> faction) {
        List<Row> rows = new ArrayList<>();
        rows.add(row("faction", faction.<Component>map(FactionVisuals::name)
                .orElse(Component.translatable(KEY + "none"))));
        faction.flatMap(spec -> Cultures.byId(spec.cultureId()))
                .ifPresent(culture -> rows.add(row("culture", Component.translatable(culture.translationKey()))));
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

    private static Row row(String labelKey, Component value) {
        return new Row(Component.translatable(KEY + labelKey), value);
    }
}
