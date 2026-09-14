CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role)
);

INSERT INTO user_roles (user_id, role)
SELECT id, role
FROM users;

CREATE INDEX idx_user_roles_role ON user_roles (role);

-- Expand only: old application instances still require this column during a
-- rolling deployment. A later release can remove it after the compatibility
-- window has closed.
ALTER TABLE users ALTER COLUMN role SET DEFAULT 'BUYER';
