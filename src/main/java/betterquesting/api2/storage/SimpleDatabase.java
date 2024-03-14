package betterquesting.api2.storage;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

public class SimpleDatabase<T> extends AbstractDatabase<T> {

    private final BitSet idMap = new BitSet();

    @Override
    public synchronized int nextID() {
        return idMap.nextClearBit(0);
    }

    @Override
    public synchronized DBEntry<T> add(int id, T value) {
        DBEntry<T> result = super.add(id, value);
        // Don't add when an exception is thrown
        idMap.set(id);
        return result;
    }

    @Override
    public synchronized boolean removeID(int key) {
        boolean result = super.removeID(key);
        if (result) idMap.clear(key);
        return result;
    }

    @Override
    public synchronized void reset() {
        super.reset();
        idMap.clear();
    }

}
