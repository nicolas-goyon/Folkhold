package io.github.nicolasgoyon.folkhold.core.sim.construction;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntToDoubleFunction;

/**
 * The share of the steps of a construction site between its builders.
 *
 * <ul>
 *   <li>Each builder works on one step at a time. It claims a step that is not applied and that no other builder
 *       claimed.</li>
 *   <li>The steps of an ordered list apply in their order: a step applies only after all the steps before it. A
 *       builder claims the first free step. Thus the builders prepare the next steps at the same time, and the order
 *       of the plan stays.</li>
 *   <li>The steps of a list with no order apply when their builders are ready. A builder claims the free step with
 *       the lowest cost, for example the step nearest to it.</li>
 *   <li>A builder can take the next step from the builder that claimed it (see {@link #takeNext}). The two builders
 *       exchange their claims, and no step stays without a builder.</li>
 * </ul>
 *
 * @param <K> the type of the ids of the builders
 */
public final class SiteSchedule<K> {
    private final int total;
    private final boolean ordered;
    private final BitSet applied = new BitSet();
    private final Map<K, Integer> claims = new HashMap<>();
    private final Map<Integer, K> owners = new HashMap<>();

    /**
     * A schedule with no applied steps.
     *
     * @param total   the number of steps
     * @param ordered true if the steps apply in their order
     */
    public SiteSchedule(int total, boolean ordered) {
        if (total < 0) {
            throw new IllegalArgumentException("total must be 0 or more: " + total);
        }
        this.total = total;
        this.ordered = ordered;
    }

    /** The number of steps. */
    public int total() {
        return total;
    }

    /** True if the steps apply in their order. */
    public boolean ordered() {
        return ordered;
    }

    /** The number of applied steps. */
    public int appliedCount() {
        return applied.cardinality();
    }

    /** True when all the steps are applied. */
    public boolean finished() {
        return appliedCount() >= total;
    }

    /** True if a step is applied. */
    public boolean isApplied(int index) {
        return applied.get(index);
    }

    /** The first step that is not applied, or {@link #total()} when all the steps are applied. */
    public int next() {
        return Math.min(total, applied.nextClearBit(0));
    }

    /** The step that a builder claimed, or empty. */
    public OptionalInt claimOf(K builder) {
        Integer index = claims.get(builder);
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    /** The builder that claimed a step, or empty. */
    public Optional<K> owner(int index) {
        return Optional.ofNullable(owners.get(index));
    }

    /**
     * Claims a step for a builder: the step that the builder claimed before, or the first free step: not applied, and
     * not claimed by another builder.
     *
     * @param builder the builder
     * @return the step, or empty when no step is free
     */
    public OptionalInt claim(K builder) {
        return claim(builder, index -> index);
    }

    /**
     * Claims a step for a builder: the step that the builder claimed before, or a free step: not applied, and not
     * claimed by another builder. In an ordered list, the free step is the first one. In a list with no order, it is
     * the free step with the lowest cost, and the first one of the steps with the same cost.
     *
     * @param builder the builder
     * @param cost    the cost of a step for the builder, for example its distance to the block of the step
     * @return the step, or empty when no step is free
     */
    public OptionalInt claim(K builder, IntToDoubleFunction cost) {
        Integer current = claims.get(builder);
        if (current != null) {
            return OptionalInt.of(current);
        }
        int best = -1;
        double bestCost = Double.MAX_VALUE;
        for (int index = next(); index < total; index++) {
            if (applied.get(index) || owners.containsKey(index)) {
                continue;
            }
            if (ordered) {
                best = index;
                break;
            }
            double value = cost.applyAsDouble(index);
            if (best < 0 || value < bestCost) {
                best = index;
                bestCost = value;
            }
        }
        if (best < 0) {
            return OptionalInt.empty();
        }
        claims.put(builder, best);
        owners.put(best, builder);
        return OptionalInt.of(best);
    }

    /** Removes the claim of a builder: another builder can claim its step. */
    public void release(K builder) {
        Integer index = claims.remove(builder);
        if (index != null) {
            owners.remove(index);
        }
    }

    /**
     * True if a step can apply now: it is not applied, and in an ordered list all the steps before it are applied.
     *
     * @param index the step
     * @return true if the step can apply
     */
    public boolean canApply(int index) {
        return index >= 0 && index < total && !applied.get(index) && (!ordered || index == next());
    }

    /**
     * Records that a step is applied. Its claim ends.
     *
     * @param index the step
     */
    public void markApplied(int index) {
        if (index < 0 || index >= total) {
            throw new IndexOutOfBoundsException("No step " + index + " in " + total + " steps");
        }
        applied.set(index);
        K owner = owners.remove(index);
        if (owner != null) {
            claims.remove(owner);
        }
    }

    /**
     * Gives the next step (see {@link #next()}) to a builder. The builder that claimed the next step gets the step of
     * this builder, or no step.
     *
     * @param builder the builder
     * @return the next step, or empty when all the steps are applied
     */
    public OptionalInt takeNext(K builder) {
        int next = next();
        if (next >= total) {
            return OptionalInt.empty();
        }
        K owner = owners.get(next);
        if (builder.equals(owner)) {
            return OptionalInt.of(next);
        }
        Integer mine = claims.remove(builder);
        if (mine != null) {
            owners.remove(mine);
        }
        if (owner != null) {
            claims.remove(owner);
            if (mine != null) {
                claims.put(owner, mine);
                owners.put(mine, owner);
            }
        }
        claims.put(builder, next);
        owners.put(next, builder);
        return OptionalInt.of(next);
    }
}
