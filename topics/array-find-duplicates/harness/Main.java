import visual.TestKit;

/**
 * Hidden test harness for the Find Duplicates challenge. It calls the learner's
 * Solution on a set of cases and reports each via visual.TestKit (TEST events).
 * The learner never sees or edits this file. Results must be distinct duplicated
 * values, sorted ascending.
 */
public class Main {

    public static void main(String[] args) {
        Solution s = new Solution();
        TestKit.expect("no duplicates", new int[] {}, s.findDuplicates(new int[] {1, 2, 3, 4}));
        TestKit.expect("empty array", new int[] {}, s.findDuplicates(new int[] {}));
        TestKit.expect("single element", new int[] {}, s.findDuplicates(new int[] {7}));
        TestKit.expect("each duplicate once", new int[] {2, 3}, s.findDuplicates(new int[] {1, 2, 2, 3, 3, 3}));
        TestKit.expect("result sorted", new int[] {2, 3}, s.findDuplicates(new int[] {4, 3, 2, 1, 2, 3}));
        TestKit.expect("all same value", new int[] {5}, s.findDuplicates(new int[] {5, 5, 5, 5}));
        TestKit.expect("negatives and zero", new int[] {-1, 0}, s.findDuplicates(new int[] {-1, -1, 0, 0, 1}));
        TestKit.expect("unsorted input", new int[] {1, 9}, s.findDuplicates(new int[] {9, 1, 9, 4, 1}));
    }
}
