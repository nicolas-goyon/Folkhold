package io.github.nicolasgoyon.folkhold.core.sim.garrison;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Chooses the posts (tower positions) of the archers of a village.
 *
 * <ul>
 *   <li><b>Calm</b>: an archer that moves goes to a random free post. The weight of a post is its view score, so
 *       a post with a large view to the outside has a higher chance.</li>
 *   <li><b>Alert</b>: each archer goes to the nearest free post. If there are more archers than posts, the
 *       remaining archers share the nearest posts.</li>
 *   <li>A post with a view score below the minimum is not valid. No archer goes there.</li>
 * </ul>
 */
public final class PostAssignment {

    /**
     * A post.
     *
     * @param x    block X of the position where the archer stands
     * @param y    block Y of this position
     * @param z    block Z of this position
     * @param view the view score: number of open lines of sight to the outside
     */
    public record Post(int x, int y, int z, int view) {

        long distanceSquared(int otherX, int otherY, int otherZ) {
            long dx = (long) x - otherX;
            long dy = (long) y - otherY;
            long dz = (long) z - otherZ;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /**
     * Position of an archer.
     *
     * @param x block X
     * @param y block Y
     * @param z block Z
     */
    public record Position(int x, int y, int z) {
    }

    private PostAssignment() {
    }

    /**
     * A random free post for a calm archer, with the view score as weight.
     *
     * @param posts   all posts of the village
     * @param taken   the indexes of the posts that other archers use
     * @param current the index of the current post of the archer, or -1
     * @param minView the minimum view score of a valid post
     * @param random  the random source
     * @return the index of the new post, or empty if no other valid post is free
     */
    public static OptionalInt pickCalm(List<Post> posts, Set<Integer> taken, int current, int minView,
            RandomGenerator random) {
        int total = 0;
        for (int i = 0; i < posts.size(); i++) {
            if (isCandidate(posts, taken, current, minView, i)) {
                total += posts.get(i).view();
            }
        }
        if (total <= 0) {
            return OptionalInt.empty();
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < posts.size(); i++) {
            if (isCandidate(posts, taken, current, minView, i)) {
                roll -= posts.get(i).view();
                if (roll < 0) {
                    return OptionalInt.of(i);
                }
            }
        }
        return OptionalInt.empty();
    }

    private static boolean isCandidate(List<Post> posts, Set<Integer> taken, int current, int minView, int index) {
        return index != current && !taken.contains(index) && posts.get(index).view() >= minView;
    }

    /**
     * The nearest free valid post for each archer. The archer with the shortest distance to a free post chooses
     * first. When no valid post is free, an archer gets its nearest valid post.
     *
     * @param archers the positions of the archers
     * @param posts   all posts of the village
     * @param minView the minimum view score of a valid post
     * @return for each archer, the index of its post, or -1 if the village has no valid post
     */
    public static int[] assignNearest(List<Position> archers, List<Post> posts, int minView) {
        int[] result = new int[archers.size()];
        Arrays.fill(result, -1);
        List<Integer> valid = new ArrayList<>();
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).view() >= minView) {
                valid.add(i);
            }
        }
        if (valid.isEmpty()) {
            return result;
        }
        Set<Integer> free = new HashSet<>(valid);
        Set<Integer> waiting = new HashSet<>();
        for (int i = 0; i < archers.size(); i++) {
            waiting.add(i);
        }
        while (!waiting.isEmpty() && !free.isEmpty()) {
            int bestArcher = -1;
            int bestPost = -1;
            long bestDistance = Long.MAX_VALUE;
            for (int archer : waiting) {
                Position position = archers.get(archer);
                for (int post : free) {
                    long distance = posts.get(post).distanceSquared(position.x(), position.y(), position.z());
                    boolean sameDistance = distance == bestDistance
                            && (archer < bestArcher || (archer == bestArcher && post < bestPost));
                    if (distance < bestDistance || sameDistance) {
                        bestDistance = distance;
                        bestArcher = archer;
                        bestPost = post;
                    }
                }
            }
            result[bestArcher] = bestPost;
            waiting.remove(bestArcher);
            free.remove(bestPost);
        }
        for (int archer : waiting) {
            Position position = archers.get(archer);
            result[archer] = valid.stream()
                    .min(Comparator.comparingLong((Integer post) -> posts.get(post)
                            .distanceSquared(position.x(), position.y(), position.z())).thenComparing(post -> post))
                    .orElse(-1);
        }
        return result;
    }
}
