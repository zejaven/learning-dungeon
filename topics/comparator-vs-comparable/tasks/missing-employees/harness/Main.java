import visual.TestKit;

import java.util.List;

/** Hidden tests for the TreeSet ranking task. */
public class Main {

    private static List<String> rank(Employee... employees) {
        return Payroll.ranking(List.of(employees)).stream().map(e -> e.name + "#" + e.id).toList();
    }

    public static void main(String[] args) {
        TestKit.run(
                TestKit.test("equal salaries do not lose anyone", 2,
                        () -> rank(new Employee(1, "Ann", 100), new Employee(2, "Bob", 100)).size()),
                TestKit.test("the highest salary comes first", List.of("Cid#3", "Ann#1"),
                        () -> rank(new Employee(1, "Ann", 100), new Employee(3, "Cid", 200))),
                TestKit.test("equal salaries are ordered by name", List.of("Ann#2", "Bob#1"),
                        () -> rank(new Employee(1, "Bob", 100), new Employee(2, "Ann", 100))),
                TestKit.test("namesakes are ordered by id", List.of("Ann#1", "Ann#2"),
                        () -> rank(new Employee(2, "Ann", 100), new Employee(1, "Ann", 100))),
                TestKit.test("one id is listed once", 1,
                        () -> rank(new Employee(1, "Ann", 100), new Employee(1, "Ann", 100)).size())
        );
    }
}
