package betterquesting.api2.storage;

import java.util.Collections;
import java.util.List;

public class EmptyLookupLogic<T> extends LookupLogic<T> {

    public EmptyLookupLogic(AbstractDatabase<T> abstractDatabase) {
        super(abstractDatabase);
    }

    @Override
    public List<DBEntry<T>> bulkLookup(int[] keys) {
        return Collections.emptyList();
    }
}
