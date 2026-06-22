import visual.TestKit;

/**
 * Hidden test harness for the Employee Commands challenge. It drives the
 * learner's Solution through realistic scenarios and reports each case via
 * visual.TestKit (TEST events). The learner never sees or edits this file.
 *
 * The card format under test is:
 *   name + " " + surname + " | " + passportNumber + " | " + passportDate + " | " + salary
 */
public class Main {

    public static void main(String[] args) {
        Solution s = new Solution();

        long ann = s.addEmployee("Ann", "Lee", "AA111", "2020-01-15", 1000);
        long bob = s.addEmployee("Bob", "Fox", "BB222", "2019-06-30", 2000);

        TestKit.expect("first id is 1", 1L, ann);
        TestKit.expect("distinct ids", true, ann != bob);
        TestKit.expect("card after add", "Ann Lee | AA111 | 2020-01-15 | 1000", s.getCard(ann));

        s.changeSalary(ann, 1500);
        TestKit.expect("salary changed, passport intact",
                "Ann Lee | AA111 | 2020-01-15 | 1500", s.getCard(ann));

        s.changePassport(ann, "AA999", "2026-02-01");
        TestKit.expect("passport changed, salary intact",
                "Ann Lee | AA999 | 2026-02-01 | 1500", s.getCard(ann));

        TestKit.expect("other employee untouched",
                "Bob Fox | BB222 | 2019-06-30 | 2000", s.getCard(bob));

        boolean threwOnGet = false;
        try {
            s.getCard(999999L);
        } catch (Exception e) {
            threwOnGet = true;
        }
        TestKit.expect("getCard on unknown id throws", true, threwOnGet);

        boolean threwOnSalary = false;
        try {
            s.changeSalary(999999L, 100);
        } catch (Exception e) {
            threwOnSalary = true;
        }
        TestKit.expect("changeSalary on unknown id throws", true, threwOnSalary);
    }
}
