import visual.VisualLockAlternatives;

public class Playground {
    public static void main(String[] args) {
        VisualLockAlternatives.Stamped lock =
                VisualLockAlternatives.stampedLock("pointLock");

        long stamp = lock.tryOptimisticRead("reader");
        lock.writeLock("writer");
        lock.unlockWrite("writer");

        boolean valid = lock.validate("reader", stamp);
        System.out.println("optimistic read still valid? " + valid);

        if (!valid) {
            lock.readLock("reader");
            lock.unlockRead("reader");
        }
    }
}
