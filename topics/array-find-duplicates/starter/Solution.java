import java.util.Arrays;

public class Solution {

    /**
     * Returns the DISTINCT values that appear more than once in nums, in
     * ascending order. Each duplicated value appears exactly once in the result,
     * no matter how many times it occurs in the input. If there are no
     * duplicates (or the array is empty), return an empty array.
     *
     * Examples:
     *   {1, 2, 3, 4}        -> {}        (no value repeats)
     *   {1, 2, 2, 3, 3, 3}  -> {2, 3}    (each duplicate listed once)
     *   {4, 3, 2, 1, 2, 3}  -> {2, 3}    (result is sorted ascending)
     *   {5, 5, 5, 5}        -> {5}
     *
     * Two classic approaches:
     *   - HashSet: remember every value you've seen; O(n) time, O(n) memory.
     *   - Sort + scan neighbours: O(n log n) time, O(1) extra memory.
     * Either is accepted here as long as the result is correct and sorted.
     */
    public int[] findDuplicates(int[] nums) {
        // TODO: implement me, then press "Run tests".
        return new int[0];
    }
}
