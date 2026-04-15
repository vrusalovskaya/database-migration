CREATE TABLE users (
                       id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                       name VARCHAR(100)
);

CREATE TABLE profiles (
                          id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                          user_id BIGINT UNSIGNED UNIQUE,
                          bio TEXT,
                          FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE orders (
                        id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT UNSIGNED,
                        total DECIMAL(10,2),
                        FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE products (
                          id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                          name VARCHAR(100)
);

CREATE TABLE order_products (
                                order_id BIGINT UNSIGNED,
                                product_id BIGINT UNSIGNED,
                                PRIMARY KEY (order_id, product_id),
                                FOREIGN KEY (order_id) REFERENCES orders(id),
                                FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE INDEX idx_orders_user_id ON orders(user_id);