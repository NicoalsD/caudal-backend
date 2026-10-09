-- V44: privileges per column for secrets and credentials (DB doc, section 4).
-- caudal_app reads password_hash only to verify a login or a change of password, and can update
-- the account columns listed below; nothing else. caudal_readonly never reaches iam nor any
-- secret (S) column. The final check fails the migration if a secret became readable.

REVOKE ALL ON iam.users FROM caudal_app, caudal_readonly;
GRANT INSERT ON iam.users TO caudal_app;
GRANT SELECT (id, username, full_name, status, must_change_password, password_changed_at,
              failed_login_count, locked_until, lockout_level, token_version,
              last_login_at, created_at, created_by, updated_at, disabled_at, password_hash)
  ON iam.users TO caudal_app;
GRANT UPDATE (full_name, status, must_change_password, password_hash, password_changed_at,
              failed_login_count, locked_until, lockout_level, token_version,
              last_login_at, last_login_ip_hmac, updated_at, disabled_at)
  ON iam.users TO caudal_app;

-- Hashes of tokens and codes are written once and never updated by the application.
REVOKE UPDATE (token_hash) ON iam.refresh_tokens FROM caudal_app;
REVOKE UPDATE (code_hash) ON iam.password_reset_grants FROM caudal_app;
REVOKE UPDATE (code_hash) ON iam.mfa_recovery_codes FROM caudal_app;
REVOKE UPDATE (secret_ciphertext) ON iam.mfa_factors FROM caudal_app;
REVOKE UPDATE (key_hash) ON iam.api_keys FROM caudal_app;
REVOKE UPDATE (tracking_code_hash) ON ops.incidents FROM caudal_app;

REVOKE ALL ON SCHEMA iam FROM caudal_readonly;

DO $secrets$
DECLARE
  v_column record;
BEGIN
  FOR v_column IN
    SELECT * FROM (VALUES
      ('iam.users', 'password_hash'), ('iam.password_history', 'password_hash'),
      ('iam.refresh_tokens', 'token_hash'), ('iam.password_reset_grants', 'code_hash'),
      ('iam.mfa_recovery_codes', 'code_hash'), ('iam.mfa_factors', 'secret_ciphertext'),
      ('iam.api_keys', 'key_hash'), ('ops.incidents', 'tracking_code_hash')) AS s(tbl, col)
  LOOP
    IF has_column_privilege('caudal_readonly', v_column.tbl, v_column.col, 'SELECT') THEN
      RAISE EXCEPTION 'caudal_readonly can read %.%', v_column.tbl, v_column.col;
    END IF;
    IF has_column_privilege('caudal_app', v_column.tbl, v_column.col, 'UPDATE')
       AND v_column.tbl <> 'iam.users' THEN
      RAISE EXCEPTION 'caudal_app can update %.%', v_column.tbl, v_column.col;
    END IF;
  END LOOP;
END
$secrets$;
