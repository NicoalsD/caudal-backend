-- V47: demo aqueduct with simulated data (docs/Datos-simulados.md; facts, sections 15, 17, 21).
-- Everything here is simulated (is_demo = true) and must be shown as "Datos simulados". There is
-- no real data of the region. The values of the rule set are the example of the seed; the Board
-- changes them by creating a new version, never by editing this migration.
--
-- The seed author is a system account that can never log in: its password_hash is not a PHC
-- string, so no password verifies against it, and the account is DISABLED.

DO $demo$
DECLARE
  v_system uuid;
  v_aqueduct uuid;
  v_tank uuid;
  v_rule_set uuid;
  v_import uuid;
  v_sector record;
  v_rows integer;
BEGIN
  INSERT INTO iam.users (username, full_name, password_hash, status, must_change_password, disabled_at)
  VALUES ('caudal.system', 'Sistema CAUDAL', '!seed-account-without-password', 'DISABLED', true, now())
  RETURNING id INTO v_system;

  INSERT INTO org.aqueducts (slug, name, municipality, department, timezone, is_demo)
  VALUES ('vereda-demo', 'Vereda Demo (datos simulados)', 'Guaitarilla', 'Nariño', 'America/Bogota', true)
  RETURNING id INTO v_aqueduct;

  PERFORM set_config('app.aqueduct_id', v_aqueduct::text, true);

  INSERT INTO org.tanks (aqueduct_id, name, gauge_min, gauge_max, gauge_step, capacity_liters)
  VALUES (v_aqueduct, 'Tanque principal', 0.00, 5.00, 0.05, 20000)
  RETURNING id INTO v_tank;

  INSERT INTO org.sectors (aqueduct_id, code, name, households_count) VALUES
    (v_aqueduct, 'ESC', 'Escuela', 1),
    (v_aqueduct, 'ALTO', 'El Alto', 18),
    (v_aqueduct, 'LOMA', 'La Loma', 24),
    (v_aqueduct, 'LLANO', 'El Llano', 15);

  INSERT INTO org.valves (sector_id, code, name, location_hint)
  SELECT s.id, 'V-' || s.code, 'Válvula ' || s.name, 'Caja de válvula junto al camino'
    FROM org.sectors s WHERE s.aqueduct_id = v_aqueduct;

  INSERT INTO org.rule_sets (
      aqueduct_id, version, status, reserve_level, max_daily_service_hours, min_shift_hours,
      max_shift_hours, duplicate_window_minutes, max_level_change_per_hour, stale_reading_hours,
      max_backdate_days, forecast_horizon_days, forecast_context_days, trend_threshold_per_day,
      created_by)
  VALUES (v_aqueduct, 1, 'DRAFT', 1.00, 16.00, 1.00, 4.00, 30, 0.50, 12, 7, 3, 30, 0.20, v_system)
  RETURNING id INTO v_rule_set;

  INSERT INTO org.rule_level_bands (rule_set_id, band, min_level, max_level, daily_service_hours, priority_only) VALUES
    (v_rule_set, 'CRITICAL', 0.00, 1.50, 3.00, true),
    (v_rule_set, 'LOW', 1.50, 3.50, 8.00, false),
    (v_rule_set, 'HIGH', 3.50, 5.00, 16.00, false);

  INSERT INTO org.rule_sector_settings (rule_set_id, sector_id, is_included, is_priority, priority_rank)
  SELECT v_rule_set, s.id, true, s.code = 'ESC', CASE WHEN s.code = 'ESC' THEN 1 END
    FROM org.sectors s WHERE s.aqueduct_id = v_aqueduct;

  INSERT INTO org.rule_valve_orders (rule_set_id, valve_id, sequence_order)
  SELECT v_rule_set, v.id, row_number() OVER (ORDER BY s.code = 'ESC' DESC, s.code)
    FROM org.valves v JOIN org.sectors s ON s.id = v.sector_id
   WHERE s.aqueduct_id = v_aqueduct;

  INSERT INTO org.rule_operating_windows (rule_set_id, day_of_week, start_time, end_time)
  VALUES (v_rule_set, NULL, '05:00', '21:00');

  UPDATE org.rule_sets
     SET status = 'ACTIVE', valid_from = now(), activated_by = v_system, activated_at = now(),
         change_reason = 'Versión inicial de la semilla del acueducto demo (datos simulados).'
   WHERE id = v_rule_set;

  -- Short simulated history: two readings a day for 30 days (07:00 and 17:00, Bogota time).
  v_rows := 60;
  INSERT INTO sim.import_batches (
      aqueduct_id, kind, scenario_name, seed, simulator_version, rows_received, rows_accepted,
      rows_rejected, file_sha256, imported_by)
  VALUES (v_aqueduct, 'READINGS', 'seed-inicial-backend', 20261009, 'backend-seed-1', v_rows, v_rows, 0,
          encode(digest('caudal-backend V47 seed-inicial-backend 20261009', 'sha256'), 'hex'), v_system)
  RETURNING id INTO v_import;

  INSERT INTO ops.readings (
      id, aqueduct_id, tank_id, gauge_value, water_appearance_code, observed_at, received_at,
      source, recorded_by, import_batch_id, rule_set_id, validation_status)
  SELECT uuidv7(), v_aqueduct, v_tank,
         round((2.80 + 1.40 * sin(d * 0.45) + CASE WHEN slot = 0 THEN 0.40 ELSE -0.30 END)::numeric, 2),
         CASE WHEN d % 11 = 3 THEN 'MUDDY' ELSE 'NORMAL' END,
         ts, ts, 'IMPORT', v_system, v_import, v_rule_set, 'ACCEPTED'
    FROM generate_series(1, 30) AS d,
         generate_series(0, 1) AS slot,
         LATERAL (SELECT ((current_date - d)::timestamp
                          + CASE WHEN slot = 0 THEN interval '7 hours' ELSE interval '17 hours' END)
                         AT TIME ZONE 'America/Bogota' AS ts) t;
END
$demo$;
