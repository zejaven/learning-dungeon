import java.util.NoSuchElementException;

// The GENERAL employee resource: create a new employee and read the card.
// This controller is COMPLETE and talks to the repository directly — it is given
// as a reference for the shape you will repeat for the two department controllers.
// The HTTP annotations are intentionally omitted: this structural exercise is
// about CLASS relationships, not Spring wiring.
//
// TODO (missions): build the two DEPARTMENT stacks alongside this controller.
//   1. SalaryController  -> SalaryService  -> EmployeeRepository  (payroll: changeSalary)
//   2. PassportController -> PassportService -> EmployeeRepository (HR: changePassport)
// Salary and passport must live in SEPARATE controllers AND separate services —
// that is the department split from the task, carried all the way through.
public class EmployeeController {
    private final EmployeeRepository repository;

    public EmployeeController(EmployeeRepository repository) {
        this.repository = repository;
    }

    // POST /employees
    public Employee create(Employee employee) {
        return repository.save(employee);
    }

    // GET /employees/{id}
    public Employee getById(Long id) {
        return repository.findById(id).orElseThrow(NoSuchElementException::new);
    }
}
