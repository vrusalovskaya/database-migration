WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100
)
INSERT INTO users(name)
SELECT CONCAT('User ', n) FROM seq;

INSERT INTO profiles(user_id, bio)
SELECT id, 'Bio ' || id FROM users;

INSERT INTO products(name)
SELECT 'Product ' || generate_series(1, 20);