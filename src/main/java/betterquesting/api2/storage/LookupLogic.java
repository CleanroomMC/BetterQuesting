package betterquesting.api2.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

abstract class LookupLogic<T> {

    protected final AbstractDatabase<T> abstractDatabase;
    protected List<DBEntry<T>> refCache = null;

    public LookupLogic(AbstractDatabase<T> abstractDatabase) {
        this.abstractDatabase = abstractDatabase;
    }

    public void onDataChange() {
        refCache = null;
    }

    public List<DBEntry<T>> getRefCache() {
        if (refCache != null) return refCache;
        computeRefCache();
        return refCache;
    }

    public abstract List<DBEntry<T>> bulkLookup(int[] keys);

    protected void computeRefCache() {
        List<DBEntry<T>> temp = new ArrayList<>();
        for (Map.Entry<Integer, T> entry : abstractDatabase.mapDB.entrySet()) {
            temp.add(new DBEntry<>(entry.getKey(), entry.getValue()));
        }
        refCache = Collections.unmodifiableList(temp);
    }
}
