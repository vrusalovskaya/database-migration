INSERT INTO order_products(order_id, product_id)
SELECT
    o.id,
    FLOOR(1 + (RAND() * 20))
FROM orders o;