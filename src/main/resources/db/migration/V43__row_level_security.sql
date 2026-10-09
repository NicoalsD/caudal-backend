-- V43: isolation by aqueduct with FORCE ROW LEVEL SECURITY on 37 tables (DB doc, section 5).
-- The application fixes app.aqueduct_id (and app.user_id at login) with set_config(..., true)
-- inside each transaction, taken only from the validated JWT. Without it no row is visible: the
-- comparison with NULL fails closed.

CREATE FUNCTION audit.current_aqueduct_id()
RETURNS uuid
LANGUAGE sql
STABLE
SET search_path = pg_catalog
AS $$
  SELECT NULLIF(current_setting('app.aqueduct_id', true), '')::uuid;
$$;

CREATE FUNCTION audit.current_user_id()
RETURNS uuid
LANGUAGE sql
STABLE
SET search_path = pg_catalog
AS $$
  SELECT NULLIF(current_setting('app.user_id', true), '')::uuid;
$$;

REVOKE ALL ON FUNCTION audit.current_aqueduct_id() FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.current_user_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit.current_aqueduct_id() TO caudal_app, caudal_readonly;
GRANT EXECUTE ON FUNCTION audit.current_user_id() TO caudal_app, caudal_readonly;

-- Tables with their own aqueduct_id: standard tenant policy.
DO $tenant$
DECLARE
  v_table text;
BEGIN
  FOREACH v_table IN ARRAY ARRAY[
    'org.tanks', 'org.sectors', 'org.rule_sets',
    'ops.sync_batches', 'ops.readings', 'ops.reading_issues', 'ops.reading_corrections',
    'ops.anomalies', 'ops.forecast_runs', 'ops.forecast_points', 'ops.forecast_evaluations',
    'ops.schedule_proposals', 'ops.schedule_items', 'ops.schedule_item_reasons',
    'ops.proposal_snapshots', 'ops.proposal_decisions', 'ops.publications',
    'ops.shift_executions', 'ops.day_closures', 'ops.incidents', 'ops.incident_status_history',
    'reporting.minutes', 'reporting.summary_shares',
    'devices.devices', 'devices.device_keys', 'devices.telemetry_points',
    'devices.valve_commands', 'devices.valve_command_events',
    'audit.audit_log', 'audit.data_access_log',
    'sim.import_batches']
  LOOP
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', v_table);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', v_table);
    EXECUTE format(
      'CREATE POLICY tenant ON %s '
      'USING (aqueduct_id = audit.current_aqueduct_id()) '
      'WITH CHECK (aqueduct_id = audit.current_aqueduct_id())', v_table);
  END LOOP;
END
$tenant$;

-- Child tables without aqueduct_id: the parent decides (its own RLS filters the subquery).
ALTER TABLE org.valves ENABLE ROW LEVEL SECURITY;
ALTER TABLE org.valves FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant ON org.valves
  USING (EXISTS (SELECT 1 FROM org.sectors s WHERE s.id = sector_id))
  WITH CHECK (EXISTS (SELECT 1 FROM org.sectors s WHERE s.id = sector_id));

DO $rule_children$
DECLARE
  v_table text;
BEGIN
  FOREACH v_table IN ARRAY ARRAY[
    'org.rule_level_bands', 'org.rule_sector_settings',
    'org.rule_valve_orders', 'org.rule_operating_windows']
  LOOP
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', v_table);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', v_table);
    EXECUTE format(
      'CREATE POLICY tenant ON %s '
      'USING (EXISTS (SELECT 1 FROM org.rule_sets r WHERE r.id = rule_set_id)) '
      'WITH CHECK (EXISTS (SELECT 1 FROM org.rule_sets r WHERE r.id = rule_set_id))', v_table);
  END LOOP;
END
$rule_children$;

-- Memberships: also the user's own rows, needed at login before an aqueduct is chosen (5.3).
ALTER TABLE iam.memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam.memberships FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_or_self ON iam.memberships
  USING (aqueduct_id = audit.current_aqueduct_id() OR user_id = audit.current_user_id())
  WITH CHECK (aqueduct_id = audit.current_aqueduct_id());

-- Rows without aqueduct in audit_log only come from maintenance functions (purges of global
-- tables). Only the object owner, inside those SECURITY DEFINER functions, sees or writes them.
CREATE POLICY global_maintenance ON audit.audit_log
  USING (aqueduct_id IS NULL AND audit.is_object_owner())
  WITH CHECK (aqueduct_id IS NULL AND audit.is_object_owner());
