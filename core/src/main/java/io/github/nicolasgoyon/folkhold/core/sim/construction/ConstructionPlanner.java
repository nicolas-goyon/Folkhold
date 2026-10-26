package io.github.nicolasgoyon.folkhold.core.sim.construction;

import io.github.nicolasgoyon.folkhold.core.sim.construction.PlanWarning.Problem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Makes the plan of a construction: the steps that change the blocks of the level into the blocks of a target.
 *
 * <p>Rules:
 * <ul>
 *   <li>A block of the level that is equal to the block of the target stays. A block with another state is an
 *       obstacle: the plan removes it, and places the block of the target. A block that needs an obstacle as its
 *       support also goes, and comes back if it is in the target.</li>
 *   <li>The removals come first: the attached blocks first, then from the top to the bottom.</li>
 *   <li>The placements go from the bottom to the top. A block comes after the block below it, and after its support.
 *       For example, a falling block comes after the block below it, and an attached block after its support. A
 *       connected block comes after the solid blocks next to it. One step places the two parts of a block of two
 *       parts.</li>
 *   <li>The fluids come at the end: the plan places a waterlogged block with no water, then adds the water.</li>
 *   <li>Each step has a position where the builder stands, with the block of the step in the reach zone of the
 *       builder (see {@link Reach}). This position has space for the builder and a floor below it. The floor is a
 *       block of the level or of the scaffolding. It can also be a block of the plan on a block of the level, for
 *       example the floor of a house. A block of the plan in a square of 2 × 2 floor blocks is a floor too, for
 *       example an upper floor. A builder does not stand on the top of a new wall, which is 1 block wide.</li>
 *   <li>The plan selects a position where a villager can walk from the ground, or a position on a scaffolding
 *       column (see {@link WalkSearch}). Of two positions, the plan selects the position with the block nearer to the
 *       eyes of the builder.</li>
 *   <li>When there is no such position, the plan adds a column of scaffolding next to the block, on positions that
 *       the target does not fill. A column on the ground comes before a column on a block of the plan, then the
 *       column with the fewest new blocks: a column on top of an existing column makes it higher. The plan removes
 *       all the scaffolding at the end, from the top.</li>
 *   <li>With no way from the ground to a column, the builder goes directly to the nearest position to stand. With
 *       no position to stand, the plan adds a column with no way to it.</li>
 *   <li>The plan reports the problems of the target (see {@link PlanWarning}). Examples: a falling block above air,
 *       an attached block with no support.</li>
 * </ul>
 */
public final class ConstructionPlanner {
    /**
     * The height of the eyes of a builder above its feet, in blocks. Of two positions to stand, the plan selects the
     * position with the block of the step nearer to the eyes.
     */
    static final double EYE_HEIGHT = 1.6;
    /** A scaffolding column has this number of blocks at most. */
    static final int MAX_SCAFFOLDING = 32;

    private final Target target;
    private final LevelView level;
    private final Reach reach;
    /** The search of a way from the ground to a position to stand, in the box of the target and the reach around it. */
    private final WalkSearch walks;
    /** The blocks of the level that the steps change, during the plan. */
    private final Map<BuildPos, BuildBlock> changes = new HashMap<>();
    /** The positions where a step of the plan put a block of the target. The scaffolding is not in this set. */
    private final Set<BuildPos> placed = new HashSet<>();
    /** The target positions that a later step fills: no scaffolding there. */
    private final Set<BuildPos> toFill = new HashSet<>();
    private final List<List<BuildPos>> columns = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    /** The positions that a removal step of the plan empties later: a scaffolding column does not stand on them. */
    private final Set<BuildPos> pendingRemovals = new HashSet<>();
    private final List<PlanWarning> warnings = new ArrayList<>();

    private ConstructionPlanner(Target target, LevelView level, Reach reach) {
        this.target = target;
        this.level = level;
        this.reach = reach;
        this.walks = WalkSearch.around(target.blocks().keySet(), reach, pos -> current(pos).walk(),
                this::isScaffolding);
    }

    /**
     * Makes the plan of a construction.
     *
     * @param target the target
     * @param level  the blocks of the level before the construction
     * @param reach  the zone where a builder places and removes blocks (see {@link Reach#DEFAULT})
     * @return the plan
     */
    public static Plan plan(Target target, LevelView level, Reach reach) {
        return new ConstructionPlanner(target, level, reach).run();
    }

    private Plan run() {
        Set<BuildPos> remove = new HashSet<>();
        Set<BuildPos> place = new HashSet<>();
        Set<BuildPos> fluids = new LinkedHashSet<>();
        for (Map.Entry<BuildPos, BuildBlock> entry : target.blocks().entrySet()) {
            BuildPos pos = entry.getKey();
            BuildBlock have = level.blockAt(pos);
            if (have.sameBlock(entry.getValue())) {
                continue;
            }
            if (have.kind() != BlockKind.AIR) {
                remove.add(pos);
            }
            markToPlace(pos, place, fluids);
        }
        addBrokenBlocks(remove, place, fluids);
        checkSupports(place);

        for (BuildPos pos : place) {
            toFill.add(pos);
            target.at(pos).partner().ifPresent(toFill::add);
        }
        toFill.addAll(fluids);

        pendingRemovals.addAll(remove);
        for (BuildPos pos : removalOrder(remove)) {
            BuildBlock block = level.blockAt(pos);
            Optional<BuildPos> stand = standFor(pos, block.partner());
            pendingRemovals.remove(pos);
            block.partner().ifPresent(pendingRemovals::remove);
            steps.add(new Step(StepType.REMOVE, pos, BuildBlock.AIR_STATE, block.partner(),
                    block.partner().map(partner -> BuildBlock.AIR_STATE), Optional.empty(), stand));
            change(pos, BuildBlock.AIR);
            block.partner().ifPresent(partner -> change(partner, BuildBlock.AIR));
        }
        for (BuildPos pos : placementOrder(place)) {
            BuildBlock block = target.at(pos);
            Optional<BuildPos> partner = block.partner();
            toFill.remove(pos);
            partner.ifPresent(toFill::remove);
            Optional<BuildPos> stand = standFor(pos, partner);
            steps.add(new Step(StepType.PLACE, pos, block.dryState().orElse(block.state()), partner,
                    partner.map(other -> target.at(other).state()), block.data(), stand));
            change(pos, block);
            placed.add(pos);
            partner.ifPresent(other -> {
                change(other, target.at(other));
                placed.add(other);
            });
        }
        List<BuildPos> fluidOrder = new ArrayList<>(fluids);
        fluidOrder.sort(bottomToTop());
        for (BuildPos pos : fluidOrder) {
            BuildBlock block = target.at(pos);
            toFill.remove(pos);
            Optional<BuildPos> stand = standFor(pos, Optional.empty());
            steps.add(new Step(StepType.ADD_FLUID, pos, block.state(), Optional.empty(), Optional.empty(), block.data(),
                    stand));
            change(pos, block);
            placed.add(pos);
        }
        for (int i = columns.size() - 1; i >= 0; i--) {
            List<BuildPos> column = columns.get(i);
            for (int j = column.size() - 1; j >= 0; j--) {
                BuildPos cell = column.get(j);
                steps.add(new Step(StepType.REMOVE_SCAFFOLDING, cell, BuildBlock.AIR_STATE, Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty()));
                change(cell, BuildBlock.AIR);
            }
        }
        return new Plan(steps, warnings);
    }

    /** Changes the block of a position for the next steps of the plan. */
    private void change(BuildPos pos, BuildBlock block) {
        changes.put(pos, block);
        walks.blockChanged(pos);
    }

    /** The current block of a position: after the steps of the plan so far. */
    private BuildBlock current(BuildPos pos) {
        BuildBlock changed = changes.get(pos);
        return changed != null ? changed : level.blockAt(pos);
    }

    /**
     * Adds a position of the target to the positions to place. The step of a two-part block is on its primary part. A
     * fluid comes at the end, and a block with a fluid in it is in the two lists.
     */
    private void markToPlace(BuildPos pos, Set<BuildPos> place, Set<BuildPos> fluids) {
        BuildBlock want = target.at(pos);
        switch (want.kind()) {
            case AIR -> {
            }
            case FLUID -> fluids.add(pos);
            case TWO_PART -> place.add(want.primary() ? pos : want.partner().orElse(pos));
            default -> {
                place.add(pos);
                if (want.dryState().isPresent()) {
                    fluids.add(pos);
                }
            }
        }
    }

    /**
     * The blocks of the target that break when the plan removes their support or their other part. The plan removes
     * them first, and places them again.
     */
    private void addBrokenBlocks(Set<BuildPos> remove, Set<BuildPos> place, Set<BuildPos> fluids) {
        Deque<BuildPos> queue = new ArrayDeque<>(remove);
        while (!queue.isEmpty()) {
            BuildPos removed = queue.poll();
            List<BuildPos> broken = new ArrayList<>();
            level.blockAt(removed).partner().ifPresent(broken::add);
            for (Side side : Side.values()) {
                BuildPos next = removed.offset(side);
                BuildBlock block = level.blockAt(next);
                if (block.supportRequired() && block.support().map(s -> next.offset(s).equals(removed)).orElse(false)) {
                    broken.add(next);
                }
            }
            for (BuildPos pos : broken) {
                if (target.contains(pos) && level.blockAt(pos).kind() != BlockKind.AIR && remove.add(pos)) {
                    queue.add(pos);
                    markToPlace(pos, place, fluids);
                }
            }
        }
    }

    /** Reports the falling blocks above air and the attached blocks with no support at the end of the plan. */
    private void checkSupports(Set<BuildPos> place) {
        List<BuildPos> sorted = new ArrayList<>(place);
        sorted.sort(bottomToTop());
        for (BuildPos pos : sorted) {
            BuildBlock block = target.at(pos);
            if (!block.supportRequired() || block.support().isEmpty()) {
                continue;
            }
            BuildPos support = pos.offset(block.support().get());
            BuildBlock end = target.contains(support) ? target.at(support) : level.blockAt(support);
            if (end.kind() == BlockKind.AIR || end.kind() == BlockKind.FLUID) {
                warnings.add(new PlanWarning(block.kind() == BlockKind.FALLING ? Problem.FALLING_BLOCK_ABOVE_AIR
                        : Problem.NO_SUPPORT, pos));
            }
        }
    }

    private static Comparator<BuildPos> bottomToTop() {
        return Comparator.comparingInt(BuildPos::y).thenComparingInt(BuildPos::x).thenComparingInt(BuildPos::z);
    }

    /** The order of the removals: the blocks on a support first, then from the top to the bottom. */
    private List<BuildPos> removalOrder(Set<BuildPos> remove) {
        List<BuildPos> order = new ArrayList<>();
        for (BuildPos pos : remove) {
            BuildBlock block = level.blockAt(pos);
            // One step removes the two parts of a block: the step of the primary part.
            boolean otherPartStep = !block.primary() && block.partner().map(remove::contains).orElse(false);
            if (!otherPartStep) {
                order.add(pos);
            }
        }
        order.sort(Comparator.comparingInt((BuildPos pos) -> level.blockAt(pos).supportRequired() ? 0 : 1)
                .thenComparing(Comparator.comparingInt(BuildPos::y).reversed())
                .thenComparingInt(BuildPos::x)
                .thenComparingInt(BuildPos::z));
        return order;
    }

    /** The position of the step of a block: the primary part for the other part of a two-part block. */
    private BuildPos stepPos(BuildPos pos) {
        BuildBlock block = target.at(pos);
        return block.kind() == BlockKind.TWO_PART && !block.primary() ? block.partner().orElse(pos) : pos;
    }

    /**
     * The order of the placements: a topological order of the supports, from the bottom to the top. At the same
     * height, the solid blocks come first, then the connected blocks, the falling blocks, the two-part blocks and the
     * attached blocks.
     */
    private List<BuildPos> placementOrder(Set<BuildPos> place) {
        Map<BuildPos, Set<BuildPos>> dependencies = new HashMap<>();
        Map<BuildPos, List<BuildPos>> dependents = new HashMap<>();
        for (BuildPos pos : place) {
            BuildBlock block = target.at(pos);
            Set<BuildPos> needs = new HashSet<>();
            // A block comes after the block below it, if the block below can hold it: not after an attached block,
            // for example a lantern that hangs from this block.
            if (holds(target.at(pos.below()).kind())) {
                needs.add(stepPos(pos.below()));
            }
            block.support().ifPresent(side -> needs.add(stepPos(pos.offset(side))));
            block.partner().ifPresent(partner -> needs.add(stepPos(partner.below())));
            if (block.kind() == BlockKind.CONNECTED) {
                for (Side side : Side.HORIZONTAL) {
                    BuildPos next = pos.offset(side);
                    if (target.at(next).kind() == BlockKind.SOLID) {
                        needs.add(next);
                    }
                }
            }
            needs.remove(pos);
            needs.retainAll(place);
            dependencies.put(pos, needs);
            for (BuildPos need : needs) {
                dependents.computeIfAbsent(need, key -> new ArrayList<>()).add(pos);
            }
        }
        Comparator<BuildPos> priority = Comparator.comparingInt(BuildPos::y)
                .thenComparingInt(pos -> kindRank(target.at(pos).kind()))
                .thenComparingInt(BuildPos::x)
                .thenComparingInt(BuildPos::z);
        PriorityQueue<BuildPos> ready = new PriorityQueue<>(priority);
        Map<BuildPos, Integer> waiting = new HashMap<>();
        for (Map.Entry<BuildPos, Set<BuildPos>> entry : dependencies.entrySet()) {
            if (entry.getValue().isEmpty()) {
                ready.add(entry.getKey());
            } else {
                waiting.put(entry.getKey(), entry.getValue().size());
            }
        }
        List<BuildPos> order = new ArrayList<>();
        while (order.size() < place.size()) {
            if (ready.isEmpty()) {
                // A cycle of supports: the lowest block of the cycle comes next.
                BuildPos next = waiting.keySet().stream().min(priority).orElseThrow();
                warnings.add(new PlanWarning(Problem.SUPPORT_CYCLE, next));
                waiting.remove(next);
                ready.add(next);
            }
            BuildPos pos = ready.poll();
            order.add(pos);
            for (BuildPos dependent : dependents.getOrDefault(pos, List.of())) {
                Integer left = waiting.get(dependent);
                if (left == null) {
                    continue;
                }
                if (left == 1) {
                    waiting.remove(dependent);
                    ready.add(dependent);
                } else {
                    waiting.put(dependent, left - 1);
                }
            }
        }
        return order;
    }

    /** True if a block of this kind can hold the block above it. */
    private static boolean holds(BlockKind kind) {
        return kind == BlockKind.SOLID || kind == BlockKind.FALLING || kind == BlockKind.CONNECTED
                || kind == BlockKind.TWO_PART;
    }

    private static int kindRank(BlockKind kind) {
        return switch (kind) {
            case SOLID, FLUID, AIR -> 0;
            case CONNECTED -> 1;
            case FALLING -> 2;
            case TWO_PART -> 3;
            case ATTACHED -> 4;
        };
    }

    /**
     * The position where the builder stands for a step. The plan selects the first possible position in this order:
     * <ol>
     *   <li>The nearest position to stand with a way from the ground (see {@link WalkSearch}).</li>
     *   <li>A new scaffolding column with a way from the ground to its bottom.</li>
     *   <li>The nearest position to stand with no way from the ground. The builder goes there directly.</li>
     *   <li>A new scaffolding column with no way from the ground to its bottom.</li>
     * </ol>
     * With no position, the plan reports the problem.
     */
    private Optional<BuildPos> standFor(BuildPos pos, Optional<BuildPos> partner) {
        List<BuildPos> stands = findStands(pos, partner);
        Optional<BuildPos> stand = stands.stream().filter(walks::canReach).findFirst();
        if (stand.isEmpty()) {
            stand = buildScaffolding(pos, partner, walks::canClimb);
        }
        if (stand.isEmpty() && !stands.isEmpty()) {
            stand = Optional.of(stands.get(0));
        }
        if (stand.isEmpty()) {
            stand = buildScaffolding(pos, partner, bottom -> true);
        }
        if (stand.isEmpty()) {
            warnings.add(new PlanWarning(Problem.NO_STAND, pos));
        }
        return stand;
    }

    /** True if a position has a scaffolding block of the plan now. */
    private boolean isScaffolding(BuildPos pos) {
        return BuildBlock.SCAFFOLDING.equals(changes.get(pos));
    }

    /** The squared distance from the eyes of a builder that stands at a position to the center of a block. */
    static double eyeDistanceSquared(BuildPos stand, BuildPos block) {
        double dx = stand.x() - block.x();
        double dy = stand.y() + EYE_HEIGHT - (block.y() + 0.5);
        double dz = stand.z() - block.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * The positions in the reach zone of a block where a builder can stand now. The position with the block nearest to
     * the eyes of the builder comes first. Of two positions at the same distance, the first position of the search
     * comes first.
     */
    private List<BuildPos> findStands(BuildPos pos, Optional<BuildPos> partner) {
        List<BuildPos> stands = new ArrayList<>();
        int horizontal = reach.horizontal();
        // The feet are at most reach.above() layers below the block, and at most reach.below() layers above it.
        for (int dy = -reach.above(); dy <= reach.below(); dy++) {
            for (int dx = -horizontal; dx <= horizontal; dx++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    BuildPos stand = pos.offset(dx, dy, dz);
                    if (canStand(stand, pos, partner)) {
                        stands.add(stand);
                    }
                }
            }
        }
        // The sort keeps the order of the search for two equal distances.
        stands.sort(Comparator.comparingDouble(stand -> eyeDistanceSquared(stand, pos)));
        return stands;
    }

    /**
     * True if a builder can stand at a position now for the step of a block. The position needs space for the feet
     * and the head, and a floor below it (see {@link #standsOn}). The builder does not stand in the block of the step,
     * or on it.
     */
    private boolean canStand(BuildPos stand, BuildPos pos, Optional<BuildPos> partner) {
        Set<BuildPos> step = new HashSet<>();
        step.add(pos);
        partner.ifPresent(step::add);
        return !step.contains(stand) && !step.contains(stand.above()) && !step.contains(stand.below())
                && current(stand).passable() && current(stand.above()).passable() && standsOn(stand.below());
    }

    /**
     * True if a builder can stand on a block now. The block is a floor, and it is one of these blocks:
     * <ul>
     *   <li>a block of the level, or the scaffolding of the plan</li>
     *   <li>a block that the plan put directly on a floor of the level</li>
     *   <li>a block of the plan in a square of 2 × 2 floor blocks now (see {@link #inWideFloor})</li>
     * </ul>
     * A builder does not climb on the other blocks of the plan, for example on the top of a new wall.
     */
    private boolean standsOn(BuildPos floor) {
        if (!current(floor).floor()) {
            return false;
        }
        if (!placed.contains(floor)) {
            return true;
        }
        BuildPos below = floor.below();
        return (!placed.contains(below) && current(below).floor()) || inWideFloor(floor);
    }

    /**
     * True if a floor block is in a square of 2 × 2 floor blocks now, at the same height. A floor of a house on its
     * foundation, the floor of an upper storey or the top of a platform is in such a square. The top of a wall of 1
     * block is not.
     */
    private boolean inWideFloor(BuildPos floor) {
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                if (current(floor.offset(dx, 0, 0)).floor() && current(floor.offset(0, 0, dz)).floor()
                        && current(floor.offset(dx, 0, dz)).floor()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds a scaffolding column next to a block: the builder stands on the column, with the block at the height of its
     * head. When the space at the height of the head is not free, for example under a roof, the block can be higher
     * than the head, in the reach zone. The column goes down to a floor, on positions that are empty now and that no
     * later step fills. Of the possible columns at one height, the plan selects:
     * <ol>
     *   <li>a column on a block of the level or on scaffolding before a column on a block of the plan. Thus the columns
     *       stand around a new house, and not on its floor, when possible;</li>
     *   <li>then the column with the fewest new blocks. Thus a column on top of an existing column makes it
     *       higher.</li>
     * </ol>
     *
     * @param climbable true if a builder can go to the bottom of a column, to climb it. The bottom of a column on top
     *                  of an existing column is the bottom of the existing column.
     * @return the position where the builder stands, on the column, or empty if no column is possible
     */
    private Optional<BuildPos> buildScaffolding(BuildPos pos, Optional<BuildPos> partner,
            Predicate<BuildPos> climbable) {
        BuildPos bestStand = null;
        List<BuildPos> bestCells = List.of();
        boolean bestOnPlan = false;
        // aboveFeet = 1: the block is at the height of the head.
        for (int aboveFeet = 1; aboveFeet <= reach.above() && bestStand == null; aboveFeet++) {
            for (int ring = 1; ring <= reach.horizontal(); ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                            continue;
                        }
                        BuildPos stand = new BuildPos(pos.x() + dx, pos.y() - aboveFeet, pos.z() + dz);
                        boolean inStep = stand.equals(pos) || stand.above().equals(pos)
                                || partner.map(other -> stand.equals(other) || stand.above().equals(other))
                                        .orElse(false);
                        if (inStep || !reach.contains(stand, pos) || !freeForScaffolding(stand)
                                || !freeForScaffolding(stand.above())) {
                            continue;
                        }
                        List<BuildPos> cells = columnBelow(stand);
                        if (cells.isEmpty()) {
                            continue;
                        }
                        // The cells go from the top down: the floor of the column is below the last cell.
                        BuildPos lowest = cells.get(cells.size() - 1);
                        boolean onPlan = placed.contains(lowest.below());
                        boolean better = bestStand == null || (!onPlan && bestOnPlan)
                                || (onPlan == bestOnPlan && cells.size() < bestCells.size());
                        if (better && climbable.test(columnBottom(lowest))) {
                            bestStand = stand;
                            bestCells = cells;
                            bestOnPlan = onPlan;
                        }
                    }
                }
            }
        }
        if (bestStand == null) {
            return Optional.empty();
        }
        List<BuildPos> column = new ArrayList<>(bestCells);
        Collections.reverse(column);
        for (BuildPos cell : column) {
            steps.add(new Step(StepType.PLACE_SCAFFOLDING, cell, BuildBlock.SCAFFOLDING_STATE, Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty()));
            change(cell, BuildBlock.SCAFFOLDING);
        }
        columns.add(column);
        return Optional.of(bestStand);
    }

    /**
     * The positions of a scaffolding column below a stand position, from the top down to a floor. The list is empty
     * when no column is possible. Causes: a position is not free, the column is too high, a floor is below the stand,
     * a later step removes the floor, or a builder cannot stand on the floor (see {@link #standsOn}).
     */
    private List<BuildPos> columnBelow(BuildPos stand) {
        List<BuildPos> cells = new ArrayList<>();
        BuildPos cell = stand.below();
        while (!current(cell).floor()) {
            if (!freeForScaffolding(cell) || cells.size() >= MAX_SCAFFOLDING) {
                return List.of();
            }
            cells.add(cell);
            cell = cell.below();
        }
        return pendingRemovals.contains(cell) || !standsOn(cell) ? List.of() : cells;
    }

    /** The bottom of a column: the lowest new cell, or the bottom of the scaffolding of the plan below it. */
    private BuildPos columnBottom(BuildPos lowest) {
        BuildPos bottom = lowest;
        while (isScaffolding(bottom.below())) {
            bottom = bottom.below();
        }
        return bottom;
    }

    /** True if a position is empty now and no later step fills it. */
    private boolean freeForScaffolding(BuildPos pos) {
        return current(pos).kind() == BlockKind.AIR && !toFill.contains(pos);
    }
}
