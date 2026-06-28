import visual.VisualOrdering;

import java.util.Comparator;

public class Playground {
    static class Employee implements Comparable<Employee> {
        final int id;
        final String name;

        Employee(int id, String name) {
            this.id = id;
            this.name = name;
        }

        int id() {
            return id;
        }

        String name() {
            return name;
        }

        @Override
        public int compareTo(Employee other) {
            return Integer.compare(this.id, other.id);
        }

        @Override
        public String toString() {
            return id + ":" + name;
        }
    }

    public static void main(String[] args) {
        Employee nina = new Employee(20, "Nina");
        Employee alex = new Employee(10, "Alex");

        VisualOrdering<Employee> byId = VisualOrdering.natural(
                "employees by id",
                "employee id",
                "employee id");
        byId.compare(nina, alex);

        VisualOrdering<Employee> byName = VisualOrdering.usingComparator(
                "employees by name",
                Comparator.comparing(Employee::name),
                "employee name",
                "employee name");
        byName.compare(nina, alex);
    }
}
