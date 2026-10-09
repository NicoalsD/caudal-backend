-- V39: append-only tables and updated_at (DB doc, sections 6 and 8).
-- Two layers protect the 23 append-only tables: caudal_app only has SELECT and INSERT, and these
-- triggers reject UPDATE, DELETE and TRUNCATE from anybody. The only exception is DELETE run by
-- the object owner, which happens inside the SECURITY DEFINER purge functions (V37).

CREATE FUNCTION audit.forbid_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, audit
AS $$
BEGIN
  IF TG_OP = 'DELETE' AND audit.is_object_owner() THEN
    RETURN OLD;
  END IF;
  RAISE EXCEPTION 'append_only_violation'
    USING ERRCODE = 'insufficient_privilege',
          HINT = format('%I.%I es append-only', TG_TABLE_SCHEMA, TG_TABLE_NAME);
END;
$$;

CREATE FUNCTION audit.forbid_truncate()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, audit
AS $$
BEGIN
  RAISE EXCEPTION 'append_only_violation'
    USING ERRCODE = 'insufficient_privilege',
          HINT = format('%I.%I es append-only', TG_TABLE_SCHEMA, TG_TABLE_NAME);
END;
$$;

DO $append_only$
DECLARE
  v_table text;
BEGIN
  FOREACH v_table IN ARRAY ARRAY[
    'iam.login_attempts', 'iam.password_history', 'iam.privacy_notice_versions',
    'iam.privacy_acceptances', 'iam.security_events',
    'ops.sync_batches', 'ops.readings', 'ops.reading_issues', 'ops.reading_corrections',
    'ops.forecast_runs', 'ops.forecast_points', 'ops.forecast_evaluations',
    'ops.proposal_snapshots', 'ops.proposal_decisions', 'ops.publications',
    'ops.shift_executions', 'ops.incident_status_history',
    'devices.telemetry_points', 'devices.valve_command_events',
    'audit.audit_log', 'audit.audit_anchors', 'audit.data_access_log',
    'sim.import_batches']
  LOOP
    EXECUTE format(
      'CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON %s '
      'FOR EACH ROW EXECUTE FUNCTION audit.forbid_mutation()', v_table);
    EXECUTE format(
      'CREATE TRIGGER append_only_truncate BEFORE TRUNCATE ON %s '
      'FOR EACH STATEMENT EXECUTE FUNCTION audit.forbid_truncate()', v_table);
    EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON %s FROM caudal_app', v_table);
  END LOOP;
END
$append_only$;

DO $updated_at$
DECLARE
  v_table text;
BEGIN
  FOREACH v_table IN ARRAY ARRAY[
    'iam.users', 'org.aqueducts', 'org.tanks', 'org.sectors', 'audit.retention_policies']
  LOOP
    EXECUTE format(
      'CREATE TRIGGER set_updated_at BEFORE UPDATE ON %s '
      'FOR EACH ROW EXECUTE FUNCTION audit.set_updated_at()', v_table);
  END LOOP;
END
$updated_at$;
