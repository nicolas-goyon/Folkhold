package io.github.nicolasgoyon.folkhold.core.sim.garrison;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.garrison.PostAssignment.Position;
import io.github.nicolasgoyon.folkhold.core.sim.garrison.PostAssignment.Post;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class PostAssignmentTest {
    private static final int MIN_VIEW = 4;
    /** Four corner towers. The post 3 is half in the rock: low view. */
    private static final List<Post> POSTS = List.of(new Post(0, 8, 0, 12), new Post(30, 8, 0, 4),
            new Post(0, 8, 30, 8), new Post(30, 8, 30, 1));

    @Test
    void calmPickAvoidsTheCurrentTheTakenAndTheInvalidPosts() {
        SplittableRandom random = new SplittableRandom(1);
        for (int i = 0; i < 200; i++) {
            OptionalInt pick = PostAssignment.pickCalm(POSTS, Set.of(1), 0, MIN_VIEW, random);
            assertEquals(OptionalInt.of(2), pick);
        }
    }

    @Test
    void calmPickPrefersTheLargerView() {
        SplittableRandom random = new SplittableRandom(2);
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < 6000; i++) {
            PostAssignment.pickCalm(POSTS, Set.of(), -1, MIN_VIEW, random)
                    .ifPresent(post -> counts.merge(post, 1, Integer::sum));
        }
        // Weights 12, 4 and 8 (the post 3 is not valid).
        assertTrue(counts.get(0) > counts.get(2) && counts.get(2) > counts.get(1), "Counts: " + counts);
        assertEquals(null, counts.get(3));
    }

    @Test
    void calmPickWithNoFreeValidPostGivesNothing() {
        assertTrue(PostAssignment.pickCalm(POSTS, Set.of(1, 2), 0, MIN_VIEW, new SplittableRandom(3)).isEmpty());
    }

    @Test
    void alertGivesEachArcherTheNearestFreePost() {
        List<Post> posts = List.of(new Post(0, 8, 0, 10), new Post(30, 8, 0, 10), new Post(0, 8, 9, 10));
        // The archer 1 is nearer to the post 0. The archer 0 then gets the post 2, its nearest free post.
        List<Position> archers = List.of(new Position(3, 1, 0), new Position(1, 1, 1));
        assertArrayEquals(new int[] {2, 0}, PostAssignment.assignNearest(archers, posts, MIN_VIEW));
    }

    @Test
    void moreArchersThanPostsShareTheNearestPosts() {
        List<Post> posts = List.of(new Post(0, 8, 0, 10));
        List<Position> archers = List.of(new Position(0, 1, 0), new Position(5, 1, 5));
        assertArrayEquals(new int[] {0, 0}, PostAssignment.assignNearest(archers, posts, MIN_VIEW));
    }

    @Test
    void noValidPostGivesMinusOne() {
        List<Post> posts = List.of(new Post(0, 8, 0, 1));
        assertArrayEquals(new int[] {-1}, PostAssignment.assignNearest(List.of(new Position(0, 1, 0)), posts,
                MIN_VIEW));
    }
}
