import visual.VisualCallStack;

public class Playground {
    public static void main(String[] args) {
        // The cure: a base case the recursion actually reaches. In real Java:
        //
        //     static long factorial(int n) {
        //         if (n <= 1) return 1;       // base case, reached every time
        //         return n * factorial(n - 1);
        //     }
        //
        // factorial(3) pushes three frames, then each one returns, popping every
        // frame back off. The stack never fills up — no StackOverflowError.
        VisualCallStack stack = new VisualCallStack(6);
        stack.call("factorial"); // n = 3
        stack.call("factorial"); // n = 2
        stack.call("factorial"); // n = 1 -> base case
        stack.ret();
        stack.ret();
        stack.ret();
    }
}
