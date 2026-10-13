package io.github.nicolasgoyon.folkhold.core.sim.group;

import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstract state of a group of NPCs that travels between two settlements: a caravan or a traveling merchant.
 *
 * <p>The group moves on its route on each abstract tick (see {@link GroupSimulation}). It has entities only near a
 * player. Each member keeps a stable UUID, so the entities of a group do not duplicate.
 */
public final class MobileGroup {
    private final String id;
    private final GroupKind kind;
    private final String factionId;
    private String originId;
    private String destinationId;
    private Route route;
    private double progress;
    private GroupState state = GroupState.WAITING;
    private long departureTick;
    private long payment;
    private final Stock cargo = new Stock();
    private final List<GroupMember> members = new ArrayList<>();

    /**
     * Makes a group in the state {@link GroupState#WAITING}, at the start of its route.
     *
     * @param id            the id of the group (a UUID as text)
     * @param kind          the kind of the group
     * @param factionId     the id of the faction of the group
     * @param originId      the id of the origin settlement
     * @param destinationId the id of the destination settlement
     * @param route         the route from the origin to the destination
     */
    public MobileGroup(String id, GroupKind kind, String factionId, String originId, String destinationId,
            Route route) {
        this.id = Objects.requireNonNull(id);
        this.kind = Objects.requireNonNull(kind);
        this.factionId = Objects.requireNonNull(factionId);
        this.originId = Objects.requireNonNull(originId);
        this.destinationId = Objects.requireNonNull(destinationId);
        this.route = Objects.requireNonNull(route);
    }

    public String id() {
        return id;
    }

    public GroupKind kind() {
        return kind;
    }

    public String factionId() {
        return factionId;
    }

    public String originId() {
        return originId;
    }

    public String destinationId() {
        return destinationId;
    }

    public Route route() {
        return route;
    }

    /**
     * Gives the group a new journey. The progress goes back to 0.
     *
     * @param originId      the id of the new origin settlement
     * @param destinationId the id of the new destination settlement
     * @param route         the route from the new origin to the new destination
     */
    public void setJourney(String originId, String destinationId, Route route) {
        this.originId = Objects.requireNonNull(originId);
        this.destinationId = Objects.requireNonNull(destinationId);
        this.route = Objects.requireNonNull(route);
        this.progress = 0.0;
    }

    /** The distance from the start of the route, in blocks (0 to the length of the route). */
    public double progress() {
        return progress;
    }

    /**
     * Sets the progress. The value is clamped to the route.
     *
     * @param progress the distance from the start of the route, in blocks
     */
    public void setProgress(double progress) {
        this.progress = Math.max(0.0, Math.min(progress, route.length()));
    }

    /** The position of the group on its route. */
    public Route.Position position() {
        return route.positionAt(progress);
    }

    public GroupState state() {
        return state;
    }

    public void setState(GroupState state) {
        this.state = Objects.requireNonNull(state);
    }

    /** The number of the abstract tick when a waiting group leaves. */
    public long departureTick() {
        return departureTick;
    }

    public void setDepartureTick(long departureTick) {
        this.departureTick = departureTick;
    }

    /** The emeralds that the destination pays to the origin when the group arrives. */
    public long payment() {
        return payment;
    }

    public void setPayment(long payment) {
        this.payment = Math.max(0, payment);
    }

    /** The goods that the group carries. */
    public Stock cargo() {
        return cargo;
    }

    /** The members, in the order of addition. */
    public List<GroupMember> members() {
        return Collections.unmodifiableList(members);
    }

    /**
     * Adds a member.
     *
     * @param member the member. Its UUID must not be in the group.
     */
    public void addMember(GroupMember member) {
        if (member(member.id()).isPresent()) {
            throw new IllegalArgumentException("Duplicate member: " + member.id());
        }
        members.add(member);
    }

    /** The member with a UUID. */
    public Optional<GroupMember> member(UUID memberId) {
        return members.stream().filter(member -> member.id().equals(memberId)).findFirst();
    }

    /** The merchant of the group, if the group has one. */
    public Optional<GroupMember> merchant() {
        return members.stream().filter(member -> member.role() == MemberRole.MERCHANT).findFirst();
    }

    /**
     * Records the death of a member. When the merchant dies, the group is lost.
     *
     * @param memberId the UUID of the member
     * @return true if the group has this member
     */
    public boolean onMemberDied(UUID memberId) {
        Optional<GroupMember> member = member(memberId);
        if (member.isEmpty()) {
            return false;
        }
        member.get().setHealth(0.0F);
        if (member.get().role() == MemberRole.MERCHANT) {
            state = GroupState.LOST;
        }
        return true;
    }

    /** True if the state is {@link GroupState#ARRIVED} or {@link GroupState#LOST}. */
    public boolean isFinished() {
        return state == GroupState.ARRIVED || state == GroupState.LOST;
    }

    @Override
    public String toString() {
        return kind.serializedName() + " " + id + " (" + factionId + ", " + originId + " -> " + destinationId + ", "
                + state.serializedName() + ", " + Math.round(progress) + "/" + Math.round(route.length()) + ")";
    }
}
