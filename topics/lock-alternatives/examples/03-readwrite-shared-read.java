import visual.VisualLockAlternatives;

public class Playground {
    public static void main(String[] args) {
        VisualLockAlternatives.ReadWrite lock =
                VisualLockAlternatives.readWriteLock("catalogLock");

        lock.readLock("reader-1");
        lock.readLock("reader-2");
        lock.writeLock("writer");

        lock.unlockRead("reader-1");
        lock.unlockRead("reader-2");
        lock.unlockWrite("writer");
    }
}
