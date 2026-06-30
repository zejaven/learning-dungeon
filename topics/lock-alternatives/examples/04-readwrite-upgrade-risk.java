import visual.VisualLockAlternatives;

public class Playground {
    public static void main(String[] args) {
        VisualLockAlternatives.ReadWrite lock =
                VisualLockAlternatives.readWriteLock("cacheLock");

        lock.readLock("cache-loader");
        boolean upgraded = lock.upgradeToWriteLock("cache-loader");
        System.out.println("upgraded directly? " + upgraded);

        lock.unlockRead("cache-loader");
        lock.writeLock("cache-loader");
        lock.unlockWrite("cache-loader");
    }
}
