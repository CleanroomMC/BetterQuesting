package betterquesting.api2.storage;

import java.util.Random;

public class RandomIndexDatabase<T> extends AbstractDatabase<T> {

    private final Random random = new Random();

    @Override
    public synchronized int nextID() {
        int id;
        do {
            // id >= 0
            id = random.nextInt() & 0x7fff_ffff;
        }
        // The new id doesn't conflict with existing ones.
        // However, new ids created by different players could conflict with each other.
        while (mapDB.containsKey(id));
        return id;
    }

}
