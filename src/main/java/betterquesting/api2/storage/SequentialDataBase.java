package betterquesting.api2.storage;

import java.util.BitSet;

/**
 * a database implementation specialized for ids that're sequential, dense and not randomly generated
 * <p>
 * see {@link RandomIndexDatabase} for database specialized in handling randomly generated ids
 */
public class SequentialDataBase<T> extends AbstractDatabase<T> {

    private final BitSet ids = new BitSet();
    /**
     * the "smallest acceptable" id, any id smaller than this is considered as used and can
     * be skipped when searching for new id, this condition can be maintained by:
     * - refresh lowerBound after new id is generated: {@link #nextID()}
     * - refresh lowerBound after one id is removed: {@link #removeID(int)}
     */
    private int lowerBound = 0;

    @Override
    public synchronized int nextID() {
        int next = ids.nextClearBit(lowerBound);
        lowerBound = next + 1;
        return next;
    }

    @Override
    public synchronized DBEntry<T> add(int id, T value) {
        DBEntry<T> result = super.add(id, value);
        // Don't add when an exception is thrown
        ids.set(id);
        // lowerBound = id; //no, lowerBound will not be refreshed here, delay to next `nextID()` call instead
        return result;
    }

    @Override
    public synchronized boolean removeID(int key) {
        boolean result = super.removeID(key);
        if (result) {
            ids.clear(key);
            lowerBound = Math.min(key, lowerBound);
        }
        return result;
    }

    @Override
    public synchronized void reset() {
        super.reset();
        ids.clear();
    }
}
