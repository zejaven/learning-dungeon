CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(50));
CREATE TABLE products  (id INT PRIMARY KEY, name VARCHAR(50));
CREATE TABLE orders    (id INT PRIMARY KEY, customer_id INT, product_id INT);

INSERT INTO customers (id, name) VALUES
  (1, 'Anna'), (2, 'Boris'), (3, 'Clara'), (4, 'Dmitri'), (5, 'Elena'), (6, 'Felix');

INSERT INTO products (id, name) VALUES
  (1, 'Book'), (2, 'Pen'), (3, 'Lamp'), (4, 'Mug');

-- Customers 1,2,3,5 have orders; 4 (Dmitri) and 6 (Felix) have none.
-- Products 1,2,4 were ordered; 3 (Lamp) was never ordered.
INSERT INTO orders (id, customer_id, product_id) VALUES
  (1, 1, 1),
  (2, 1, 2),
  (3, 2, 1),
  (4, 3, 4),
  (5, 5, 2),
  (6, 5, 4);
