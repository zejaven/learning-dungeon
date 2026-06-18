import visual.TestKit;

/**
 * Hidden test harness for the Max Product challenge. It calls the learner's
 * Solution on a set of cases and reports each via visual.TestKit (TEST events).
 * The learner never sees or edits this file.
 */
public class Main {

    public static void main(String[] args) {
        Solution s = new Solution();
        TestKit.expect("positives", 12, s.maxPairProduct(new int[] {1, 2, 3, 4}));
        TestKit.expect("two negatives win", 30, s.maxPairProduct(new int[] {-10, -3, 1, 2}));
        TestKit.expect("all negative", 6, s.maxPairProduct(new int[] {-1, -2, -3}));
        TestKit.expect("equal pair", 25, s.maxPairProduct(new int[] {5, 5}));
        TestKit.expect("negatives beat zero", 2, s.maxPairProduct(new int[] {0, -1, -2}));
        TestKit.expect("mixed", 42, s.maxPairProduct(new int[] {6, 7, -1, 3}));
    }
}
