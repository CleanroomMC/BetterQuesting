package betterquesting.api2.utils;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.List;

/**
 * An optimized party quest merger implementing k-way merge (using a loser tree).
 * @see <a href="https://www.ahl27.com/posts/2024/12/loser-trees-io/">implementation based on this article</a>
 */
public class PartyQuestMerger {
    private static final int EMPTY_BIN = -1;

    private final int numBins;
    /** Number of bins with data left */
    private int fullBins;

    /** The tournament tree */
    private final int[] values;
    /** Array of each party member's active quests */
    private final int[][] binSources;
    /** Index into each active quest source */
    private final int[] binIndices;
    /** Current head quest of each bin */
    private final int[] bins;

    /** The merged, sorted list of (active) quest ids for this party. */
    private final IntArrayList merged;

    // Temp values
    private int oLoser;
    private int oWinner;
    private int prevOutput = -1;

    /**
     * @param activeQuestsPerPlayer a non-empty list of sorted active quests per player
     */
    public PartyQuestMerger(List<int[]> activeQuestsPerPlayer) {
        int numBins = activeQuestsPerPlayer.size();
        // Ensure number of bins used for tree is a power of 2
        int actualBins = 1;
        while (actualBins < numBins) actualBins <<= 1;

        this.numBins = actualBins;
        this.fullBins = numBins;

        // Initialize tree indices
        values = new int[actualBins * 2];
        for (int i = 0; i < actualBins; i++) {
            // First half are the internal nodes' indices
            values[i] = -1;
            // Second half are the leaves, fill with bin indices
            values[i + actualBins] = i;
        }

        int maxNumQuests = 0;
        binSources = new int[actualBins][];
        binIndices = new int[actualBins];
        bins = new int[actualBins];
        for (int i = 0; i < bins.length; i++) {
            // Init any bins used for padding actualBins to next power of 2
            if (i >= numBins) {
                binSources[i] = new int[0];
                bins[i] = EMPTY_BIN;
                continue;
            }
            int[] playerActiveQuests = activeQuestsPerPlayer.get(i);
            binSources[i] = playerActiveQuests;
            if (playerActiveQuests.length > 0) {
                // Preload the first element
                bins[i] = playerActiveQuests[0];
                binIndices[i]++;
            }
            else {
                bins[i] = EMPTY_BIN;
            }

            maxNumQuests = Math.max(maxNumQuests, playerActiveQuests.length);
        }
        // Start with a size of the player with the most quests
        merged = new IntArrayList(maxNumQuests);
    }

    /**
     * Get the combined list of quests unlocked by party members.
     * @return array of shared quest ids
     */
    public int[] getSharedQuests() {
        buildGame();
        runGame();
        return merged.toIntArray();
    }

    private void playGame() {
        if (bins[oWinner] == EMPTY_BIN) {
            return;
        }
        if (bins[oLoser] == EMPTY_BIN || bins[oLoser] > bins[oWinner]) {
            swapOutcome();
        }
    }

    private int playRecursiveGameAtNodeI(int i) {
        if (i >= numBins) {
            return i - numBins;
        }
        int left = playRecursiveGameAtNodeI(2 * i);
        int right = playRecursiveGameAtNodeI((2 * i) + 1);
        // The loser of the game is promoted
        int loser, winner;
        if (bins[right] == EMPTY_BIN) {
            // if right is empty, left loses
            winner = right;
            loser = left;
        }
        else if (bins[left] == EMPTY_BIN) {
            // if left is empty, right loses
            winner = left;
            loser = right;
        }
        else {
            // play the game
            if (bins[right] > bins[left]) {
                winner = right;
                loser = left;
            }
            else {
                winner = left;
                loser = right;
            }
        }
        // in loser trees, the loser is promoted, winner stays
        values[i] = winner;
        return loser;
    }

    private void buildGame() {
        values[0] = playRecursiveGameAtNodeI(1);
    }

    private void popOutput() {
        int curMin = values[0];
        // Deduplicate ids before outputting
        int id = bins[curMin];
        if (id != prevOutput) {
            merged.add(id);
        }
        prevOutput = id;

        if (binIndices[curMin] < binSources[curMin].length) {
            // if there's still elements in the bin, get the next element
            int[] binSource = binSources[curMin];
            bins[curMin] = binSource[binIndices[curMin]++];
        }
        else {
            // otherwise the bin is empty
            bins[curMin] = EMPTY_BIN;
            fullBins--;
        }
    }

    private void runGame() {
        popOutput();
        while (fullBins > 0) {
            int lastPopped = values[0];
            int curNode = lastPopped + numBins;
            oLoser = lastPopped;

            while (curNode > 0) {
                oWinner = values[curNode];
                playGame();
                // Winner stays at this node
                values[curNode] = oWinner;
                // Move to parent node
                curNode /= 2;
            }

            // Loser is promoted
            values[curNode] = oLoser;
            // Pop the next output
            popOutput();
        }
    }

    private void swapOutcome() {
        int temp = oLoser;
        oLoser = oWinner;
        oWinner = temp;
    }
}
