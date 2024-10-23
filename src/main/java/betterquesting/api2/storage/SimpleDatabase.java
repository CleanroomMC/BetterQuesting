package betterquesting.api2.storage;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public class SimpleDatabase<T> extends AbstractDatabase<T> {

    private final IntOpenHashSet ids = new IntOpenHashSet();

    @Override
    public synchronized int nextID() {
        for (int i = 0; i < Integer.MAX_VALUE ; i++) {
            if (!ids.contains(i)) {
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
        return result;
    }

    @Override
    public synchronized boolean removeID(int key) {
        boolean result = super.removeID(key);
        if (result) {
            ids.remove(key);
        }
        return result;
    }

    @Override
    public synchronized void reset() {
        super.reset();
        ids.clear();
    }
}
