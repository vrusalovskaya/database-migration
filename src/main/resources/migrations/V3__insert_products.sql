INSERT INTO orders(user_id, total)
SELECT (random()*99 + 1)::int, random()*100
FROM generate_series(1, 150);

INSERT INTO order_products(order_id, product_id)
SELECT o.id, (random()*19 + 1)::int
FROM orders o;