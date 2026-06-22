import java.time.LocalDate;

// The domain entity. Given to you — complete. Your services mutate it through
// the repository; the read side maps it to the response card.
public class Employee {
    private Long id;
    private String name;
    private String surname;
    private String passportNumber;
    private LocalDate passportDate;
    private long salary;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }

    public String getPassportNumber() { return passportNumber; }
    public void setPassportNumber(String passportNumber) { this.passportNumber = passportNumber; }

    public LocalDate getPassportDate() { return passportDate; }
    public void setPassportDate(LocalDate passportDate) { this.passportDate = passportDate; }

    public long getSalary() { return salary; }
    public void setSalary(long salary) { this.salary = salary; }
}
