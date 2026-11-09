import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/** An employee. {@code id} is unique; names and salaries are not. */
final class Employee {
    final int id;
    final String name;
    final int salary;

    Employee(int id, String name, int salary) {
        this.id = id;
        this.name = name;
        this.salary = salary;
    }
}

final class Payroll {
    /** The salary ranking: highest salary first. */
    static SortedSet<Employee> ranking(List<Employee> employees) {
        SortedSet<Employee> ranking = new TreeSet<>(
                Comparator.comparingInt((Employee e) -> e.salary).reversed()
                        .thenComparing((Employee e) -> e.name)
                        .thenComparingInt(e -> e.id));
        ranking.addAll(employees);
        return ranking;
    }
}
