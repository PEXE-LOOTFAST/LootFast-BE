-- Merchant accounts migrated from the old single-role model must retain buyer access.
INSERT INTO user_roles (user_id, role)
SELECT user_id, 'BUYER'
FROM user_roles
WHERE role = 'MERCHANT'
ON CONFLICT DO NOTHING;
