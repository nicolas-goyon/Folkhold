package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable;
import io.github.nicolasgoyon.folkhold.core.sim.village.PlotUses.Building;
import io.github.nicolasgoyon.folkhold.core.sim.village.PlotUses.Use;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Plot;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.PlotRole;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan.Start;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlotUsesTest {
    private static final int PLAZA = 15;

    /** The requirements of the data file of the mod. */
    private static final List<VillageBuildings.Requirement> REQUIREMENTS = List.of(
            new VillageBuildings.Requirement("storehouse", 3), new VillageBuildings.Requirement("barracks", 9),
            new VillageBuildings.Requirement("storehouse", 9), new VillageBuildings.Requirement("barracks", 17),
            new VillageBuildings.Requirement(VillageBuildings.MARKET, 5));

    /**
     * The buildings of a level with the homes that hold them: the Sunreach storehouses and barracks are courtyard
     * houses, the market of the Valeholm style fits a long house.
     */
    private static List<Building> buildings(StreetStyle style, int level, boolean market) {
        List<Building> buildings = new ArrayList<>();
        for (String kind : VillageBuildings.kinds(REQUIREMENTS, level)) {
            if (kind.equals(VillageBuildings.MARKET)) {
                if (market) {
                    buildings.add(new Building(kind,
                            style == StreetStyle.VALEHOLM ? style.home().name() : VillageBuildings.MARKET));
                }
            } else {
                buildings.add(new Building(kind,
                        style == StreetStyle.SUNREACH ? "courtyard_house" : style.home().name()));
            }
        }
        return buildings;
    }

    /**
     * Each tier and style, at the top level of the tier, with and without the market: each building has a plot that
     * holds it, the job slots of the level have a plot, the posts take the plots of the first jobs first,
     * the spare plots stay free, and the wide plot of the market stays free with no market.
     */
    @Test
    void eachBuildingAndPostHasAPlot() {
        for (VillageSize size : VillageSizeTable.DEFAULT.sizes()) {
            int level = LevelTable.DEFAULT.tiers().stream().filter(tier -> tier.id().equals(size.tier())).findFirst()
                    .orElseThrow().maxLevel();
            for (StreetStyle base : StreetStyle.ALL) {
                for (boolean market : List.of(true, false)) {
                    List<Building> buildings = buildings(base, level, market);
                    boolean hasMarket = buildings.stream()
                            .anyMatch(building -> building.kind().equals(VillageBuildings.MARKET));
                    StreetStyle style = PlotUses.withWide(base, buildings);
                    VillagePlan plan = VillagePlan.plan(size, size.plots(0, 0), style, PLAZA, Start.around(PLAZA),
                            box -> true, 1);
                    int posts = LevelTable.DEFAULT.traits(level).jobSlots();
                    PlotUses.Result result = PlotUses.assign(plan, buildings, posts);
                    String name = size.tier() + " " + base.name() + " market " + market;
                    assertEquals(List.of(), result.missing(), name);
                    assertEquals(posts, result.posts(), name);
                    List<Use> uses = result.uses();
                    assertEquals(plan.plots().size(), uses.size(), name);
                    long free = 0;
                    for (int i = 0; i < uses.size(); i++) {
                        Plot plot = plan.plots().get(i);
                        Use use = uses.get(i);
                        if (plot.spare() || (!hasMarket && plot.wide()
                                && plot.homes().get(1).name().equals(VillageBuildings.MARKET))) {
                            assertEquals(PlotUses.Type.FREE, use.type(), name);
                            free++;
                        }
                        if (plot.role() == PlotRole.JOBS_FOOD) {
                            assertEquals(new Use(PlotUses.Type.POST, "", 0), use, name);
                        }
                        if (plot.role() == PlotRole.BANK) {
                            assertEquals(PlotUses.Type.BANK, use.type(), name);
                        }
                        if (use.type() == PlotUses.Type.BUILDING) {
                            Building building = buildings.stream().filter(b -> b.kind().equals(use.kind()))
                                    .findFirst().orElseThrow();
                            assertTrue(plot.homes().get(plot.homes().size() - 1).name().equals(building.home()),
                                    name);
                        }
                    }
                    assertEquals(free, uses.stream().filter(use -> use.type() == PlotUses.Type.FREE).count(), name);
                    assertEquals(buildings.size(), uses.stream().filter(use -> use.type() == PlotUses.Type.BUILDING).count(),
                            name);
                    assertEquals(result, PlotUses.assign(plan, buildings, posts), name);
                }
            }
        }
    }

    /** A Sunreach style gets one courtyard plot for each building of 13 x 13 blocks, more than its count. */
    @Test
    void wideCountHoldsTheBuildings() {
        StreetStyle style = PlotUses.withWide(StreetStyle.SUNREACH, buildings(StreetStyle.SUNREACH, 20, true));
        assertEquals(List.of(1, 4), style.wide().stream().map(StreetStyle.Home::count).toList());
        assertEquals(StreetStyle.SUNREACH, PlotUses.withWide(StreetStyle.SUNREACH,
                buildings(StreetStyle.SUNREACH, 9, true)));
        assertEquals(StreetStyle.BASE, PlotUses.withWide(StreetStyle.BASE, buildings(StreetStyle.BASE, 20, true)));
    }

    /** With too few plots, the posts take the free plots and the result gives the missing buildings. */
    @Test
    void missingPlotsAreInTheResult() {
        VillageSize size = VillageSizeTable.DEFAULT.sizes().iterator().next();
        VillagePlan plan = VillagePlan.plan(size, size.plots(0, 0), StreetStyle.BASE, PLAZA, Start.around(PLAZA),
                box -> true, 1);
        List<Building> many = new ArrayList<>();
        for (int i = 0; i < plan.plots().size(); i++) {
            many.add(new Building("storehouse", "house"));
        }
        PlotUses.Result result = PlotUses.assign(plan, many, 3);
        assertTrue(result.missing().size() > 0);
        assertTrue(result.posts() <= 3);
    }
}
