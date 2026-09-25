INSERT INTO roles (code, name, description)
VALUES
    ('ADMIN', 'Administrator', 'Full access to blog administration'),
    ('EDITOR', 'Editor', 'Manage and publish blog content'),
    ('USER', 'User', 'Standard authenticated account')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

INSERT INTO permissions (code, description)
VALUES
    ('post:read', 'Read blog posts'),
    ('post:create', 'Create blog posts'),
    ('post:update', 'Update blog posts'),
    ('post:publish', 'Publish or unpublish blog posts'),
    ('post:delete', 'Archive or delete blog posts'),
    ('comment:create', 'Submit comments'),
    ('comment:moderate', 'Moderate comments'),
    ('message:create', 'Submit visitor messages'),
    ('message:read', 'Read visitor messages'),
    ('message:update', 'Update visitor message status'),
    ('user:manage', 'Manage user accounts'),
    ('role:manage', 'Manage roles and role assignments'),
    ('permission:manage', 'Manage permissions')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM roles
CROSS JOIN permissions
WHERE roles.code = 'ADMIN';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM roles
JOIN permissions ON permissions.code IN (
    'post:read',
    'post:create',
    'post:update',
    'post:publish',
    'comment:moderate'
)
WHERE roles.code = 'EDITOR';

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM roles
JOIN permissions ON permissions.code IN ('comment:create', 'message:create')
WHERE roles.code = 'USER';
