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
 *   <li>Each step has a position where the builder stands, in the reach of the builder. This position has a floor
 *       below it and space for the builder. When there is no such position, the plan adds a column of scaffolding
 *       next to the block, on positions that the target does not fill. The plan removes all the scaffolding at the
 *       end, from the top.</li>
 *   <li>The plan reports the problems of the target (see {@link PlanWarning}). Examples: a falling block above air,
 *       an attached block with no support.</li>
 * </ul>
 */
public final class ConstructionPlanner {
    /** The default reach of a builder, in blocks, from the eyes of the builder to the center of the block. */
    public static final double DEFAULT_REACH = 4.5;
    /** The height of the eyes of a builder above its feet, in blocks. */
    static final double EYE_HEIGHT = 1.6;
    /** A scaffolding column has this number of blocks at most. */
    static final int MAX_SCAFFOLDING = 32;
    /** The plan builds a scaffolding column at this horizontal distance from the block or nearer, in blocks. */
    private static final int SCAFFOLDING_DISTANCE = 2;

    private final Target target;
    private final LevelView level;
    private final double reach;
    /** The blocks of the level that the steps change, during the plan. */
    private final Map<BuildPos, BuildBlock> changes = new HashMap<>();
    /** The target positions that a later step fills: no scaffolding there. */
    private final Set<BuildPos> toFill = new HashSet<>();
    private final List<List<BuildPos>> columns = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    /** The positions that a removal step of the plan empties later: a scaffolding column does not stand on them. */
    private final Set<BuildPos> pendingRemovals = new HashSet<>();
    private final List<PlanWarning> warnings = new ArrayList<>();

    private ConstructionPlanner(Target target, LevelView level, double reach) {
        this.target = target;
        this.level = level;
        this.reach = reach;
    }

    /**
     * Makes the plan of a construction.
     *
     * @param target the target
     * @param level  the blocks of the level before the construction
     * @param reach  the reach of a builder, in blocks (see {@link #DEFAULT_REACH})
     * @return the plan
     */
    public static Plan plan(Target target, LevelView level, double reach) {
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
            changes.put(pos, BuildBlock.AIR);
            block.partner().ifPresent(partner -> changes.put(partner, BuildBlock.AIR));
        }
        for (BuildPos pos : placementOrder(place)) {
            BuildBlock block = target.at(pos);
            Optional<BuildPos> partner = block.partner();
            toFill.remove(pos);
            partner.ifPresent(toFill::remove);
            Optional<BuildPos> stand = standFor(pos, partner);
            steps.add(new Step(StepType.PLACE, pos, block.dryState().orElse(block.state()), partner,
                    partner.map(other -> target.at(other).state()), block.data(), stand));
            changes.put(pos, block);
            partner.ifPresent(other -> changes.put(other, target.at(other)));
        }
        List<BuildPos> fluidOrder = new ArrayList<>(fluids);
        fluidOrder.sort(bottomToTop());
        for (BuildPos pos : fluidOrder) {
            BuildBlock block = target.at(pos);
            toFill.remove(pos);
            Optional<BuildPos> stand = standFor(pos, Optional.empty());
            steps.add(new Step(StepType.ADD_FLUID, pos, block.state(), Optional.empty(), Optional.empty(), block.data(),
                    stand));
            changes.put(pos, block);
        }
        for (int i = columns.size() - 1; i >= 0; i--) {
            List<BuildPos> column = columns.get(i);
            for (int j = column.size() - 1; j >= 0; j--) {
                BuildPos cell = column.get(j);
                steps.add(new Step(StepType.REMOVE_SCAFFOLDING, cell, BuildBlock.AIR_STATE, Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty()));
                changes.put(cell, BuildBlock.AIR);
            }
        }
        return new Plan(steps, warnings);
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
     * The position where the builder stands for a step. With no such position, the plan adds a scaffolding column.
     */
    private Optional<BuildPos> standFor(BuildPos pos, Optional<BuildPos> partner) {
        Optional<BuildPos> stand = findStand(pos, partner);
        if (stand.isEmpty()) {
            stand = buildScaffolding(pos, partner);
        }
        if (stand.isEmpty()) {
            warnings.add(new PlanWarning(Problem.NO_STAND, pos));
        }
        return stand;
    }

    /** The squared distance from the eyes of a builder that stands at a position to the center of a block. */
    static double eyeDistanceSquared(BuildPos stand, BuildPos block) {
        double dx = stand.x() - block.x();
        double dy = stand.y() + EYE_HEIGHT - (block.y() + 0.5);
        double dz = stand.z() - block.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** The nearest position in reach of a block where a builder can stand now, if there is one. */
    private Optional<BuildPos> findStand(BuildPos pos, Optional<BuildPos> partner) {
        int range = (int) Math.ceil(reach) + 1;
        BuildPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -range - 1; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    BuildPos stand = pos.offset(dx, dy, dz);
                    double distance = eyeDistanceSquared(stand, pos);
                    if (distance <= reach * reach && distance < bestDistance && canStand(stand, pos, partner)) {
                        best = stand;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * True if a builder can stand at a position now for the step of a block. The position needs a floor below it, and
     * space for the feet and the head. The builder does not stand in the block of the step, or on it.
     */
    private boolean canStand(BuildPos stand, BuildPos pos, Optional<BuildPos> partner) {
        Set<BuildPos> step = new HashSet<>();
        step.add(pos);
        partner.ifPresent(step::add);
        return !step.contains(stand) && !step.contains(stand.above()) && !step.contains(stand.below())
                && current(stand).passable() && current(stand.above()).passable() && current(stand.below()).floor();
    }

    /**
     * Adds a scaffolding column next to a block: the builder stands on the column, one block below the block. The
     * column goes down to a floor, on positions that are empty now and that no later step fills.
     *
     * @return the position where the builder stands, on the column, or empty if no column is possible
     */
    private Optional<BuildPos> buildScaffolding(BuildPos pos, Optional<BuildPos> partner) {
        for (int ring = 1; ring <= SCAFFOLDING_DISTANCE; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    BuildPos stand = new BuildPos(pos.x() + dx, pos.y() - 1, pos.z() + dz);
                    boolean inStep = stand.equals(pos) || stand.above().equals(pos)
                            || partner.map(other -> stand.equals(other) || stand.above().equals(other)).orElse(false);
                    if (inStep || eyeDistanceSquared(stand, pos) > reach * reach || !freeForScaffolding(stand)
                            || !freeForScaffolding(stand.above())) {
                        continue;
                    }
                    List<BuildPos> cells = columnBelow(stand);
                    if (cells.isEmpty()) {
                        continue;
                    }
                    Collections.reverse(cells);
                    for (BuildPos cell : cells) {
                        steps.add(new Step(StepType.PLACE_SCAFFOLDING, cell, BuildBlock.SCAFFOLDING_STATE,
                                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
                        changes.put(cell, BuildBlock.SCAFFOLDING);
                    }
                    columns.add(cells);
                    return Optional.of(stand);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The positions of a scaffolding column below a stand position, from the top down to a floor. The list is empty
     * when no column is possible. Causes: a position is not free, the column is too high, a floor is below the stand,
     * or a later step removes the floor.
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
        return pendingRemovals.contains(cell) ? List.of() : cells;
    }

    /** True if a position is empty now and no later step fills it. */
    private boolean freeForScaffolding(BuildPos pos) {
        return current(pos).kind() == BlockKind.AIR && !toFill.contains(pos);
    }
}
