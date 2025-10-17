package betterquesting.api2.utils;

import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PartyQuestMergerTest {
    private static final int ELEMENTS_PER_SET = 1_000;

    private final Random random = new Random(100);
    private List<int[]> intSets;
    private int[] expected;

    public void init(int numSets, int... numElementsPerSet) {
        IntSortedSet verifySet = new IntRBTreeSet();
        intSets = new ArrayList<>();

        for (int i = 0; i < numSets; i++) {
            IntSortedSet intSet = new IntRBTreeSet();
            int numElements = numElementsPerSet[i % numElementsPerSet.length];

            for (int j = 0; j < numElements; j++) {
                int value = random.nextInt(10_000);
                verifySet.add(value);
                intSet.add(value);
            }

            intSets.add(intSet.toIntArray());
        }
        this.expected = verifySet.toIntArray();
    }

    @ParameterizedTest
    @ValueSource(ints = {
        1, // single player
        4, // power of 2
        5, // non-power of 2
    })
    public void mergeQuestsForVaryingPlayers(int numPlayers) {
        init(numPlayers, ELEMENTS_PER_SET);
        PartyQuestMerger merger = new PartyQuestMerger(intSets);
        Assertions.assertArrayEquals(expected, merger.getSharedQuests());
    }

    @Test
    public void mergeQuestsForVaryingActiveQuests() {
        init(4, 1, ELEMENTS_PER_SET, ELEMENTS_PER_SET * 2, ELEMENTS_PER_SET / 2);
        PartyQuestMerger merger = new PartyQuestMerger(intSets);
        Assertions.assertArrayEquals(expected, merger.getSharedQuests());
    }
}
