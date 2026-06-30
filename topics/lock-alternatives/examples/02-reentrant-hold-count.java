import visual.VisualLockAlternatives;

public class Playground {
    public static void main(String[] args) {
        VisualLockAlternatives.Reentrant lock =
                VisualLockAlternatives.reentrantLock("invoiceLock");

        lock.lock("service");
        lock.lock("service");
        System.out.println("hold count = " + lock.holdCount());

        lock.unlock("service");
        lock.unlock("service");
    }
}
