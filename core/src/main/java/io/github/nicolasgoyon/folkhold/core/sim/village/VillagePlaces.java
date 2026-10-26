package io.github.nicolasgoyon.folkhold.core.sim.village;

/**
 * The places of the inhabitants of a village, as the buildings in a real-time strategy game.
 *
 * <ul>
 *   <li>Each bed of the homes of the village gives one place. The heart of the village counts the beds.</li>
 *   <li>The hall of the village also gives one place to each member of the staff: the builders and the handymen.
 *       Thus a village with no bed keeps the workers that build its houses.</li>
 *   <li>The number of inhabitants is the target of the population, or the number of places when it is smaller. A
 *       village with fewer places than inhabitants keeps its inhabitants: it gets no new inhabitant.</li>
 *   <li>Before the first count of the beds, the number of beds is {@link #UNKNOWN}: the places give no limit.</li>
 * </ul>
 */
public final class VillagePlaces {
    /** The number of beds of a village before the first count. */
    public static final int UNKNOWN = -1;

    private VillagePlaces() {
    }

    /**
     * The number of places of a village.
     *
     * @param beds  the number of beds of the homes, or {@link #UNKNOWN}
     * @param staff the number of builders and handymen
     * @return the number of places, or {@link #UNKNOWN} when the number of beds is unknown
     */
    public static int places(int beds, int staff) {
        return beds < 0 ? UNKNOWN : beds + Math.max(0, staff);
    }

    /**
     * The number of inhabitants of a village: the target, with the places as the maximum.
     *
     * @param target the target of the population for the inhabitants
     * @param beds   the number of beds of the homes, or {@link #UNKNOWN}
     * @param staff  the number of builders and handymen
     * @return the number of inhabitants
     */
    public static int inhabitants(int target, int beds, int staff) {
        int places = places(beds, staff);
        return places == UNKNOWN ? Math.max(0, target) : Math.max(0, Math.min(target, places));
    }
}
