package io.github.nicolasgoyon.folkhold.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SerializedNameTest {
    private static final List<Class<? extends Enum<?>>> TYPES = List.of(GroupKind.class, GroupState.class,
            MemberRole.class, QuestKind.class, QuestState.class, ReputationTier.class, SettlementState.class);

    @Test
    void eachConstantHasItsOwnLowerCaseName() {
        for (Class<? extends Enum<?>> type : TYPES) {
            Set<String> names = new HashSet<>();
            for (Enum<?> constant : type.getEnumConstants()) {
                String name = ((SerializedName) constant).serializedName();
                assertTrue(name.matches("[a-z_]+"), type.getSimpleName() + ": " + name);
                assertTrue(names.add(name), type.getSimpleName() + " has two constants with the name " + name);
            }
        }
    }

    @Test
    void lookupGivesTheConstantBack() {
        for (GroupState state : GroupState.values()) {
            assertEquals(Optional.of(state), SerializedName.lookup(GroupState.class, state.serializedName()));
        }
        assertEquals(Optional.of(MemberRole.PACK_ANIMAL), MemberRole.fromSerializedName("pack_animal"));
        assertEquals(Optional.of(QuestKind.ESCORT), QuestKind.fromSerializedName("escort"));
    }

    @Test
    void unknownNameGivesNoConstant() {
        assertEquals(Optional.empty(), GroupKind.fromSerializedName("airship"));
        assertEquals(Optional.empty(), GroupState.fromSerializedName(""));
        assertEquals(Optional.empty(), MemberRole.fromSerializedName("Guard"), "The lookup is case sensitive");
        assertEquals(Optional.empty(), QuestState.fromSerializedName("done"));
        assertEquals(Optional.empty(), QuestKind.fromSerializedName("raid"));
    }
}
