CREATE TABLE departments (
  id INT PRIMARY KEY,
  name VARCHAR(50) NOT NULL
);

CREATE TABLE employees (
  id INT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  department_id INT,
  mentor_id INT
);

CREATE TABLE shift_templates (
  id INT PRIMARY KEY,
  name VARCHAR(50) NOT NULL
);

INSERT INTO departments (id, name) VALUES
  (1, 'Engineering'),
  (2, 'Support'),
  (3, 'HR'),
  (4, 'Finance');

INSERT INTO employees (id, name, department_id, mentor_id) VALUES
  (1, 'Anna', 1, NULL),
  (2, 'Boris', 1, 1),
  (3, 'Clara', 2, 1),
  (4, 'Denis', NULL, NULL),
  (5, 'Elena', 99, 3);

INSERT INTO shift_templates (id, name) VALUES
  (1, 'Morning'),
  (2, 'Evening');
