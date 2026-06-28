import visual.VisualCallStack;

public class Playground {
    public static void main(String[] args) {
        // A recursion that LOOKS like it terminates but never reaches its base
        // case. In real Java:
        //
        //     static int countDown(int n) {
        //         if (n == 0) return 0;   // base case
        //         return countDown(n - 2); // bug: odd n skips 0 forever
        //     }
        //
        // Called with an odd n, the argument jumps 5 -> 3 -> 1 -> -1 -> ... and
        // never equals 0, so it recurses forever and overflows.
        VisualCallStack stack = new VisualCallStack(6);
        stack.recurseUntilOverflow("countDown");
    }
}
