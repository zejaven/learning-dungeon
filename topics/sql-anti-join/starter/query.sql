-- The schema (left panel) has customers, products, and an orders table that
-- links a customer to a product they bought.
--
-- Mission 1: find customers who never placed an order  (id, name).
-- Mission 2: find products that were never ordered      (id, name).
-- Mission 3: find customers who never ordered the 'Book' (product id 1) (id, name).
--
-- The trick is the ANTI-JOIN: LEFT JOIN, then keep only the rows where the
-- right-hand side stayed NULL (no match). Edit this query and press "Run query".
SELECT c.id, c.name
FROM customers c;
