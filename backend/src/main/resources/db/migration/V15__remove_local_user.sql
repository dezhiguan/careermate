-- Retire legacy single-user bootstrap account; all access requires registered JWT login.
DELETE FROM resume_versions
WHERE user_id IN (SELECT id FROM users WHERE username = 'local-user');

DELETE FROM users
WHERE username = 'local-user';
