package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import io.github.nicolasgoyon.folkhold.core.sim.schedule.DaySchedule;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.village.schedule.VillageSchedule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The rows of the village heart screen for the life of the village: the market and the job posts.
 *
 * <ul>
 *   <li><b>market</b> (first rows of the economy section): the state of the market (see {@link VillageMarket.State}),
 *       the market hours with the open or closed state at this time, and the days to the next market day;</li>
 *   <li><b>jobs</b> (a section): the number of active jobs, one row for each job post in the order of the posts (the
 *       job, the state of the post, the workers and the beds of its home), the number of homes with no post, the
 *       legacy job slots of a legacy village and their jobs, and a row when the last check of the growth found no
 *       place for a new home.</li>
 * </ul>
 * The server makes the rows when the player opens the screen. The client gets only these rows.
 */
public final class VillageLife {
    private static final String KEY = "screen.folkhold_world.village_heart.";
    /** Color of a bad value on wood. */
    private static final int TEXT_BAD = 0xE8846F;

    private VillageLife() {
    }

    /**
     * The market rows of a village: state, hours, market day.
     *
     * @param heart      the village heart
     * @param settlement the settlement of the village
     * @return the rows
     */
    public static List<Row> marketRows(VillageHeartBlockEntity heart, Settlement settlement) {
        List<Row> rows = new ArrayList<>();
        if (!(heart.getLevel() instanceof ServerLevel level)) {
            return rows;
        }
        VillageMarket.State state = heart.marketState(level);
        Component stateText = Component.translatable(marketStateKey(state));
        rows.add(new Row(Component.translatable(KEY + "market"), isBad(state)
                ? stateText.copy().withColor(TEXT_BAD) : stateText));
        DaySchedule schedule = VillageSchedule.schedule();
        rows.add(new Row(Component.translatable(KEY + "market_hours"),
                Component.translatable(marketHoursKey(VillageSchedule.isMarketOpen(level)),
                        DaySchedule.clock(schedule.marketStart()), DaySchedule.clock(schedule.marketEnd()))));
        int days = VillageSchedule.daysToMarketDay(level, settlement);
        rows.add(new Row(Component.translatable(KEY + "market_day"), Component.translatable(marketDayKey(days),
                days == 0 ? VillageSchedule.extraOffers(level, settlement) : days)));
        return rows;
    }

    /**
     * The jobs section of a village.
     *
     * @param heart      the village heart
     * @param settlement the settlement of the village
     * @return the section
     */
    public static Section jobsSection(VillageHeartBlockEntity heart, Settlement settlement) {
        List<Row> rows = new ArrayList<>();
        MinecraftServer server = heart.getLevel() == null ? null : heart.getLevel().getServer();
        Optional<List<JobPosts.Post>> posts = settlement.jobPosts();
        int activeJobs = Economies.activeJobIds(settlement).size();
        List<String> legacyJobs = List.of();
        if (posts.isPresent()) {
            List<JobPosts.Post> list = posts.get();
            List<Integer> households = JobPosts.households(list,
                    job -> Economies.isValidJob(server, settlement, job), Economies.homeInhabitants(settlement));
            int activePosts = 0;
            for (int i = 0; i < list.size(); i++) {
                JobPosts.Post post = list.get(i);
                boolean valid = Economies.isValidJob(server, settlement, post.jobId());
                int workers = Collections.frequency(households, i);
                activePosts += valid && workers > 0 ? 1 : 0;
                Component value = Component.translatable(postStateKey(valid, workers), workers, post.homeBeds());
                rows.add(new Row(jobName(post.jobId()), valid && workers > 0 ? value
                        : value.copy().withColor(TEXT_BAD)));
            }
            heart.homes().ifPresent(homes -> rows.add(new Row(Component.translatable(KEY + "cottages"),
                    Component.literal(String.valueOf(homes.cottages())))));
            if (settlement.legacyJobs()) {
                List<String> slotJobs = settlement.economy().jobs();
                int slots = JobPosts.legacySlots(LevelTables.get().traits(settlement.level()).jobSlots(),
                        activePosts);
                legacyJobs = slotJobs.subList(0, Math.min(slotJobs.size(), slots));
            }
        }
        rows.add(0, new Row(Component.translatable(KEY + "active_jobs"), legacyJobs.isEmpty()
                ? Component.literal(String.valueOf(activeJobs))
                : Component.translatable(KEY + "active_jobs.legacy", activeJobs, legacyJobs.size())));
        if (!legacyJobs.isEmpty()) {
            rows.add(new Row(Component.translatable(KEY + "legacy_jobs"), Component.translatable(
                    KEY + "legacy_jobs.value", legacyJobs.size(),
                    ComponentUtils.formatList(legacyJobs, Component.literal(", "), VillageLife::jobName))));
        }
        if (heart.isHomeNoSpace()) {
            rows.add(new Row(Component.translatable(KEY + "new_home"),
                    Component.translatable(KEY + "new_home.no_space").withColor(TEXT_BAD)));
        }
        return new Section(Component.translatable(KEY + "jobs"), rows);
    }

    /**
     * The name of a job: the key {@code job.<namespace>.<path>}, with the path of the id as the fallback for a job of a
     * data pack with no name.
     *
     * @param jobId the job id, for example {@code folkhold_world:farmer}
     * @return the name
     */
    public static Component jobName(String jobId) {
        return Component.translatableWithFallback(jobKey(jobId), jobId.substring(jobId.indexOf(':') + 1));
    }

    /** The translation key of the name of a job. */
    static String jobKey(String jobId) {
        return "job." + jobId.replace(':', '.');
    }

    /** The text of a market state. */
    static String marketStateKey(VillageMarket.State state) {
        return KEY + "market." + state.name().toLowerCase(Locale.ROOT);
    }

    /** True for a market state that the player can change: no free plot, or a block of a player. */
    static boolean isBad(VillageMarket.State state) {
        return state == VillageMarket.State.NO_SPACE || state == VillageMarket.State.BLOCKED;
    }

    /** The text of the market hours: open or closed at this time. */
    static String marketHoursKey(boolean open) {
        return KEY + (open ? "market_hours.open" : "market_hours.closed");
    }

    /** The text of the days to the next market day: today, tomorrow, or a number of days. */
    static String marketDayKey(int days) {
        if (days <= 0) {
            return KEY + "market_day.today";
        }
        return KEY + (days == 1 ? "market_day.tomorrow" : "market_day.days");
    }

    /** The text of the state of a post: job not valid, no inhabitant, or active. */
    static String postStateKey(boolean valid, int workers) {
        if (!valid) {
            return KEY + "post.not_valid";
        }
        return KEY + (workers > 0 ? "post.active" : "post.empty");
    }
}
