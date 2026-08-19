-- V3__add_enabled_to_users.sql
-- US-7.2: Identity.7 -- a System Administrator may disable/re-enable a staff account; a disabled
-- account cannot log in until re-enabled. Defaults every existing row to TRUE so no currently
-- active account is locked out by this migration.

ALTER TABLE users
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER must_change_password;
