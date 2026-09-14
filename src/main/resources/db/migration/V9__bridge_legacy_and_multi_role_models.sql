-- Some development/preview databases applied the first version of V6, which
-- removed users.role. Restore it idempotently before installing the bridge.
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(255);

UPDATE users target
SET role = CASE
    WHEN EXISTS (
        SELECT 1 FROM user_roles roles
        WHERE roles.user_id = target.id AND roles.role = 'ADMIN'
    ) THEN 'ADMIN'
    WHEN EXISTS (
        SELECT 1 FROM user_roles roles
        WHERE roles.user_id = target.id AND roles.role = 'MERCHANT'
    ) THEN 'MERCHANT'
    ELSE 'BUYER'
END;

ALTER TABLE users ALTER COLUMN role SET DEFAULT 'BUYER';
ALTER TABLE users ALTER COLUMN role SET NOT NULL;

-- New instances write user_roles. Keep the single legacy role at the highest
-- privilege so an old instance can still authorize the same account.
CREATE OR REPLACE FUNCTION sync_user_roles_to_legacy_role()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    affected_user_id BIGINT;
    legacy_role VARCHAR(255);
BEGIN
    IF TG_OP = 'DELETE' THEN
        affected_user_id := OLD.user_id;
    ELSE
        affected_user_id := NEW.user_id;
    END IF;

    SELECT CASE
        WHEN COUNT(*) FILTER (WHERE role = 'ADMIN') > 0 THEN 'ADMIN'
        WHEN COUNT(*) FILTER (WHERE role = 'MERCHANT') > 0 THEN 'MERCHANT'
        ELSE 'BUYER'
    END
    INTO legacy_role
    FROM user_roles
    WHERE user_id = affected_user_id;

    UPDATE users
    SET role = legacy_role
    WHERE id = affected_user_id
      AND role IS DISTINCT FROM legacy_role;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_user_roles_to_legacy_role ON user_roles;
CREATE TRIGGER trg_user_roles_to_legacy_role
AFTER INSERT OR UPDATE OR DELETE ON user_roles
FOR EACH ROW
EXECUTE FUNCTION sync_user_roles_to_legacy_role();

-- Old instances write users.role. Defer this bridge until commit so a new
-- instance can insert its element collection first without a duplicate-key
-- race.
CREATE OR REPLACE FUNCTION sync_legacy_role_to_user_roles()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO user_roles (user_id, role)
    VALUES (NEW.id, NEW.role)
    ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_legacy_role_to_user_roles ON users;
CREATE CONSTRAINT TRIGGER trg_legacy_role_to_user_roles
AFTER INSERT OR UPDATE ON users
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION sync_legacy_role_to_user_roles();
