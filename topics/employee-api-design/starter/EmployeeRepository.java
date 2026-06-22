import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// In-memory store of employees. Given to you — complete. Both write services and
// the read side go through this single repository.
public class EmployeeRepository {
    private final Map<Long, Employee> byId = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public Employee save(Employee employee) {
        if (employee.getId() == null) {
            employee.setId(seq.incrementAndGet());
        }
        byId.put(employee.getId(), employee);
        return employee;
    }

    public Optional<Employee> findById(Long id) {
        return Optional.ofNullable(byId.get(id));
    }
}
