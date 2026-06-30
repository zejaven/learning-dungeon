import visual.VisualLockAlternatives;

public class Playground {
    public static void main(String[] args) {
        VisualLockAlternatives.Stamped lock =
                VisualLockAlternatives.stampedLock("settingsLock");

        lock.readLock("updater");
        long writeStamp = lock.tryConvertToWriteLock("updater");
        System.out.println("converted? " + (writeStamp != 0));

        lock.unlockWrite("updater");
    }
}
