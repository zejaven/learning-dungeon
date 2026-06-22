import java.util.HashMap;
import java.util.Map;

/**
 * In-memory logic behind the employee API, expressed as separate commands.
 * No Spring, no HTTP — just the behaviour the controllers would delegate to.
 *
 * Implement these four operations:
 *
 *   addEmployee(name, surname, passportNumber, passportDate, salary) -> long id
 *       Stores a new employee and returns a NEW unique id (ids start at 1 and
 *       increase: the first employee is 1, the second is 2, ...).
 *
 *   changeSalary(id, newSalary)
 *       Updates ONLY the salary of that employee. Must not touch the passport.
 *
 *   changePassport(id, passportNumber, passportDate)
 *       Updates ONLY the passport fields. Must not touch the salary.
 *
 *   getCard(id) -> String
 *       Returns the response card in EXACTLY this format (single spaces, " | "
 *       separators), built from the current stored values:
 *
 *           name + " " + surname + " | " + passportNumber + " | "
 *                 + passportDate + " | " + salary
 *
 *       Example: "Ann Lee | AA111 | 2020-01-15 | 1000"
 *
 * Any operation on an id that was never added (changeSalary, changePassport,
 * getCard) must throw an exception — e.g. java.util.NoSuchElementException.
 *
 * Tip: keep one small holder per employee in a Map keyed by id, and a counter
 * for the next id. Each command reads the holder and changes only its own field.
 */
public class Solution {

    public long addEmployee(String name, String surname,
                            String passportNumber, String passportDate, long salary) {
        // TODO: store a new employee and return its new unique id.
        return 0;
    }

    public void changeSalary(long id, long newSalary) {
        // TODO: update only the salary.
    }

    public void changePassport(long id, String passportNumber, String passportDate) {
        // TODO: update only the passport fields.
    }

    public String getCard(long id) {
        // TODO: return the response card for this employee.
        return null;
    }
}
