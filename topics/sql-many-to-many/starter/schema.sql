CREATE TABLE employees (id INT PRIMARY KEY, name VARCHAR(50));
CREATE TABLE courses (id INT PRIMARY KEY, name VARCHAR(50));
CREATE TABLE enrollments (employee_id INT, course_id INT);

INSERT INTO employees (id, name) VALUES
  (1, 'Anna'), (2, 'Boris'), (3, 'Clara'), (4, 'Dmitri'), (5, 'Elena'),
  (6, 'Felix'), (7, 'Galina'), (8, 'Hugo'), (9, 'Irina'), (10, 'Jonas'),
  (11, 'Klara'), (12, 'Lev'), (13, 'Marta'), (14, 'Nikolai');

INSERT INTO courses (id, name) VALUES
  (1, 'Java'), (2, 'SQL'), (3, 'Kotlin'), (4, 'Go');

-- Java (1): 12 enrolments, SQL (2): 11, Kotlin (3): 4, Go (4): 1.
INSERT INTO enrollments (employee_id, course_id) VALUES
  (1,1),(2,1),(3,1),(4,1),(5,1),(6,1),(7,1),(8,1),(9,1),(10,1),(11,1),(12,1),
  (1,2),(2,2),(3,2),(4,2),(5,2),(6,2),(7,2),(8,2),(9,2),(10,2),(11,2),
  (1,3),(2,3),(3,3),(4,3),
  (1,4);
