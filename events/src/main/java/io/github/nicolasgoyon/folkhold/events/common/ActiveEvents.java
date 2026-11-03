package io.github.nicolasgoyon.folkhold.events.common;

import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The active events of one type (earthquakes or storms) in each level.
 *
 * <ul>
 *   <li>A new event does not start when the server has {@link EventsConfig#MAX_ACTIVE_EVENTS} active events of all
 *       the types, or the largest number of active events of its type.</li>
 *   <li>A new event does not start when its area overlaps the area of an active event of the same type.</li>
 *   <li>The active events are not saved: an event stops when the server stops.</li>
 * </ul>
 *
 * @param <T> the type of the events
 */
public final class ActiveEvents<T extends WorldEvent> {
    /** The registries of all the types. They give the global count of active events. */
    private static final List<ActiveEvents<?>> TYPES = new CopyOnWriteArrayList<>();

    private final Map<ResourceKey<Level>, List<T>> active = new HashMap<>();

    public ActiveEvents() {
        TYPES.add(this);
    }

    /** The number of active events of all the types on the server. */
    public static int totalActive() {
        return TYPES.stream().mapToInt(ActiveEvents::count).sum();
    }

    /** Removes the active events of all the types, with no change to the world. For the stop of the server. */
    public static void clearAll() {
        TYPES.forEach(types -> types.active.clear());
    }

    /** The number of active events of this type on the server. */
    public int count() {
        return active.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Starts an event.
     *
     * @param level     the level
     * @param center    the center of the area
     * @param radius    the radius of the area, in blocks
     * @param maxOfType the largest number of active events of this type on the server
     * @param factory   makes the event
     * @return the event, or empty when a cap or an overlap stops it
     */
    public Optional<T> start(ServerLevel level, BlockPos center, int radius, int maxOfType, Supplier<T> factory) {
        if (totalActive() >= EventsConfig.MAX_ACTIVE_EVENTS.get() || count() >= maxOfType
                || overlaps(level, center, radius)) {
            return Optional.empty();
        }
        T event = factory.get();
        active.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(event);
        return Optional.of(event);
    }

    /** True if a circle of a level overlaps the area of an active event of this type. */
    public boolean overlaps(ServerLevel level, BlockPos center, int radius) {
        for (T event : active.getOrDefault(level.dimension(), List.of())) {
            double dx = center.getX() - event.center().getX();
            double dz = center.getZ() - event.center().getZ();
            double reach = radius + event.radius();
            if (dx * dx + dz * dz < reach * reach) {
                return true;
            }
        }
        return false;
    }

    /** The active events of this type in a level. */
    public List<T> active(ServerLevel level) {
        return List.copyOf(active.getOrDefault(level.dimension(), List.of()));
    }

    /**
     * Stops the active events of this type in a level.
     *
     * @param level the level
     * @return the number of stopped events
     */
    public int stop(ServerLevel level) {
        List<T> events = active.remove(level.dimension());
        if (events == null) {
            return 0;
        }
        events.forEach(WorldEvent::stop);
        return events.size();
    }

    /**
     * Ticks the active events of this type in a level, and removes the events that end.
     *
     * @param level the level
     * @param onEnd called for each event that ends, after its removal
     */
    public void tick(ServerLevel level, Consumer<T> onEnd) {
        List<T> events = active.get(level.dimension());
        if (events == null) {
            return;
        }
        // A copy: the loop removes the events that end.
        for (T event : List.copyOf(events)) {
            event.tick();
            if (event.isDone()) {
                events.remove(event);
                onEnd.accept(event);
            }
        }
        if (events.isEmpty()) {
            active.remove(level.dimension());
        }
    }
}
