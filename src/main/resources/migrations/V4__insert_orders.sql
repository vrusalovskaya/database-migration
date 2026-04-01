WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 150
)
INSERT INTO orders(user_id, total)
SELECT
    FLOOR(1 + (RAND() * 100)),
    ROUND(RAND() * 500, 2)
FROM seq;