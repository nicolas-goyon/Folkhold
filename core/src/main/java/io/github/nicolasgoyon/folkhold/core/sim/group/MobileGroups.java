package io.github.nicolasgoyon.folkhold.core.sim.group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The mobile groups of a world, by id, in the order of addition.
 */
public final class MobileGroups {
    private final Map<String, MobileGroup> groups = new LinkedHashMap<>();

    /**
     * Adds a group.
     *
     * @param group the group. Its id must not be in the registry.
     */
    public void add(MobileGroup group) {
        if (groups.containsKey(group.id())) {
            throw new IllegalArgumentException("Duplicate group: " + group.id());
        }
        groups.put(group.id(), group);
    }

    /**
     * Removes a group.
     *
     * @param id the id of the group
     * @return true if the registry had this group
     */
    public boolean remove(String id) {
        return groups.remove(id) != null;
    }

    public Optional<MobileGroup> byId(String id) {
        return Optional.ofNullable(groups.get(id));
    }

    /**
     * The group that has a member.
     *
     * @param memberId the UUID of the member
     * @return the group, or an empty value
     */
    public Optional<MobileGroup> byMember(UUID memberId) {
        for (MobileGroup group : groups.values()) {
            if (group.member(memberId).isPresent()) {
                return Optional.of(group);
            }
        }
        return Optional.empty();
    }

    /** The groups that have a settlement as origin or as destination. */
    public List<MobileGroup> bySettlement(String settlementId) {
        List<MobileGroup> result = new ArrayList<>();
        for (MobileGroup group : groups.values()) {
            if (group.originId().equals(settlementId) || group.destinationId().equals(settlementId)) {
                result.add(group);
            }
        }
        return result;
    }

    /** All groups, in the order of addition. */
    public Collection<MobileGroup> all() {
        return Collections.unmodifiableCollection(groups.values());
    }

    public int size() {
        return groups.size();
    }
}
