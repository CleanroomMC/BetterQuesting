package betterquesting.api2.storage;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public class SimpleDatabase<T> extends AbstractDatabase<T> {

    private final IntOpenHashSet ids = new IntOpenHashSet();
    private int lowerBound = 0;

    @Override
    public synchronized int nextID() {
        for (int i = lowerBound; i < Integer.MAX_VALUE ; i++) {
            if (!ids.contains(i)) {
                lowerBound = i + 1;
                return i;
            }
        }
        throw new IllegalStateException(String.format("All integer id from 0 to %s have been consumed", Integer.MAX_VALUE));
    }

    @Override
    public synchronized DBEntry<T> add(int id, T value) {
        DBEntry<T> result = super.add(id, value);
        // Don't add when an exception is thrown
        ids.add(id);
        // lowerBound = id; //no, lowerBound will not be refreshed here, but delayed to next `nextID()` call
        return result;
    }

    @Override
    public synchronized boolean removeID(int key) {
        boolean result = super.removeID(key);
        if (result) {
            ids.remove(key);
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
