-- V41: last line of defense for gauge values (DB doc, section 8.1).
-- A stored reading or correction is always inside the painted gauge of its tank. The API answers
-- 422 GAUGE_OUT_OF_RANGE first; this trigger rejects anything that slips through.

CREATE FUNCTION ops.check_reading_range()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, org, ops
AS $$
DECLARE
  v_min numeric(6, 2);
  v_max numeric(6, 2);
  v_value numeric(6, 2);
BEGIN
  IF TG_TABLE_NAME = 'readings' THEN
    v_value := NEW.gauge_value;
    SELECT t.gauge_min, t.gauge_max INTO STRICT v_min, v_max
      FROM org.tanks t WHERE t.id = NEW.tank_id;
  ELSE
    v_value := NEW.corrected_gauge_value;
    SELECT t.gauge_min, t.gauge_max INTO STRICT v_min, v_max
      FROM ops.readings r JOIN org.tanks t ON t.id = r.tank_id
     WHERE r.id = NEW.reading_id;
  END IF;

  IF v_value < v_min OR v_value > v_max THEN
    RAISE EXCEPTION 'gauge_out_of_range'
      USING ERRCODE = 'check_violation',
            HINT = format('valor %s fuera de la regla %s a %s', v_value, v_min, v_max);
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER reading_range
  BEFORE INSERT ON ops.readings
  FOR EACH ROW EXECUTE FUNCTION ops.check_reading_range();

CREATE TRIGGER reading_correction_range
  BEFORE INSERT ON ops.reading_corrections
  FOR EACH ROW EXECUTE FUNCTION ops.check_reading_range();
