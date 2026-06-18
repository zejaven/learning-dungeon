# Max Product of Two Numbers

> **Practice mode.** This is a *challenge* topic: implement the method in
> `Solution.java`, press **Run tests**, and a hidden harness runs your code
> against a set of cases. The mission passes when they all go green.

## The task

Given an array of integers (length ≥ 2), return the **largest product of any two
of them**.

## The trap: negatives

It's tempting to just take the two largest values. But two large **negative**
numbers multiply to a large **positive**:

```
{-10, -3, 1, 2}  ->  -10 * -3 = 30   (beats 1 * 2 = 2)
```

So the answer is the larger of:

- the product of the **two largest** values, and
- the product of the **two smallest** (most negative) values.

## Optimal solution — one pass, O(n)

You don't need to sort. Track the two largest and two smallest values in a single
scan, then compare the two candidate products:

```java
int max1 = Integer.MIN_VALUE, max2 = Integer.MIN_VALUE; // two largest
int min1 = Integer.MAX_VALUE, min2 = Integer.MAX_VALUE; // two smallest
for (int n : nums) {
    if (n > max1) { max2 = max1; max1 = n; }
    else if (n > max2) { max2 = n; }
    if (n < min1) { min2 = min1; min1 = n; }
    else if (n < min2) { min2 = n; }
}
return Math.max(max1 * max2, min1 * min2);
```

- **Time O(n)**, **space O(1)** — one pass, a few variables.
- Sorting also works (`Math.max(nums[0]*nums[1], nums[n-1]*nums[n-2])` after
  sorting), but that's **O(n log n)** — fine for small inputs, slower at scale.

## 60-second interview answer

> The maximum product of two numbers is the larger of (the two biggest values'
> product) and (the two smallest values' product) — because two large negatives
> make a large positive. I track those four numbers in a single O(n) pass with
> O(1) space and return the max of the two candidate products. Sorting would also
> work but costs O(n log n).

## Common misconceptions

- ❌ "Just multiply the two largest numbers." — Misses the two-negatives case.
- ❌ "You must sort first." — Sorting is O(n log n); a single linear scan is enough.
- ❌ "Take the absolute values." — That can pick a negative × positive pair and get
  the sign wrong; compare the actual candidate products instead.
