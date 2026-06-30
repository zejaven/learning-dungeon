import visual.VisualLockAlternatives;

public class Playground {
    public static void main(String[] args) {
        VisualLockAlternatives.Reentrant lock =
                VisualLockAlternatives.reentrantLock("orderLock", true);

        lock.lock("worker-1");
        boolean worker2Entered = lock.tryLock("worker-2");
        System.out.println("worker-2 entered? " + worker2Entered);

        lock.unlock("worker-1");
        lock.lock("worker-2");
        lock.unlock("worker-2");
    }
}
