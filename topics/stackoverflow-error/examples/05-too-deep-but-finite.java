import visual.VisualCallStack;

public class Playground {
    public static void main(String[] args) {
        // Overflow does not require a bug. A correct recursion with a real base
        // case still overflows if it simply goes TOO DEEP for the stack size:
        //
        //     static long sum(int n) {
        //         if (n == 0) return 0;        // valid base case
        //         return n + sum(n - 1);
        //     }
        //     sum(1_000_000); // depth 1,000,000 -> StackOverflowError
        //
        // Here the tiny stack holds 6 frames, so a depth-of-many recursion fills
        // it before reaching the base case. The fix is an iterative loop.
        VisualCallStack stack = new VisualCallStack(6);
        stack.recurseUntilOverflow("sum");
    }
}
