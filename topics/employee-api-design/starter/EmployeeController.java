// The web entry point. The HTTP annotations are intentionally omitted — this
// structural exercise is about CLASS relationships, not Spring wiring.
//
// TODO (missions):
//   1. Create a SalaryService that holds an EmployeeRepository field
//      (changeSalary lives here — owned by payroll).
//   2. Create a PassportService that holds an EmployeeRepository field
//      (changePassport lives here — owned by HR). Salary and passport must be
//      in SEPARATE services: that is the department split from the task.
//   3. Create an EmployeeQueryService that holds an EmployeeRepository field
//      (the read side: getCard returns name/surname/passport/salary).
//   4. Give this controller fields for SalaryService, PassportService and
//      EmployeeQueryService, and delegate each endpoint to the right one.
public class EmployeeController {

    // TODO: hold the SalaryService, PassportService and EmployeeQueryService
    // here and delegate each endpoint to the right one.
}
