CREATE TABLE app_user
(
    id                SERIAL PRIMARY KEY,
    login             VARCHAR(50)  NOT NULL UNIQUE CHECK (LENGTH(login) >= 3),
    password_hash     VARCHAR(255) NOT NULL CHECK (LENGTH(password_hash) > 0),
    registration_date TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE post
(
    id         SERIAL PRIMARY KEY,
    title      VARCHAR(200)  NOT NULL CHECK (LENGTH(title) > 0),
    content    VARCHAR(10000) NOT NULL CHECK (LENGTH(content) > 0),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    author_id  INTEGER       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE
);
