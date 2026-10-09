-- V42: hash chain of audit.audit_log (DB doc, section 7).
-- row_hash = sha256(prev_hash || canonical content), one chain per aqueduct, genesis = 64 zeros.
-- The canonical content is jsonb_build_object with a fixed set of fields; its text form has a
-- deterministic key order, so verification recomputes exactly the same string.

CREATE FUNCTION audit.audit_row_canonical(
    p_id bigint, p_occurred_at timestamptz, p_actor_type text, p_actor_id uuid, p_action text,
    p_entity_type text, p_entity_id text, p_before jsonb, p_after jsonb, p_request_id text,
    p_ip_hmac text)
RETURNS text
LANGUAGE sql
STABLE
SET search_path = pg_catalog
AS $$
  SELECT jsonb_build_object(
    'id', p_id,
    'occurred_at', to_char(p_occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'actor_type', p_actor_type,
    'actor_id', p_actor_id,
    'action', p_action,
    'entity_type', p_entity_type,
    'entity_id', p_entity_id,
    'before_state', p_before,
    'after_state', p_after,
    'request_id', p_request_id,
    'ip_hmac', p_ip_hmac)::text;
$$;

CREATE FUNCTION audit.chain_audit_row()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, public, audit
AS $$
DECLARE
  v_prev char(64);
BEGIN
  -- Serializes the inserts of one chain so two transactions never take the same prev_hash.
  PERFORM pg_advisory_xact_lock(hashtextextended('audit:' || COALESCE(NEW.aqueduct_id::text, 'global'), 0));

  SELECT row_hash INTO v_prev
    FROM audit.audit_log
   WHERE aqueduct_id IS NOT DISTINCT FROM NEW.aqueduct_id
   ORDER BY id DESC
   LIMIT 1;

  NEW.prev_hash := COALESCE(v_prev, repeat('0', 64));
  NEW.row_hash := encode(digest(NEW.prev_hash || audit.audit_row_canonical(
      NEW.id, NEW.occurred_at, NEW.actor_type, NEW.actor_id, NEW.action, NEW.entity_type,
      NEW.entity_id, NEW.before_state, NEW.after_state, NEW.request_id, NEW.ip_hmac), 'sha256'), 'hex');
  RETURN NEW;
END;
$$;

CREATE TRIGGER audit_log_chain
  BEFORE INSERT ON audit.audit_log
  FOR EACH ROW EXECUTE FUNCTION audit.chain_audit_row();

-- Returns the id of the first row whose hash does not match, or NULL when the chain is valid.
-- Runs with the caller's rights, so it only sees the aqueduct fixed in app.aqueduct_id.
CREATE FUNCTION audit.verify_chain(p_aqueduct uuid)
RETURNS bigint
LANGUAGE plpgsql
STABLE
SET search_path = pg_catalog, public, audit
AS $$
DECLARE
  v_row record;
  v_prev char(64) := repeat('0', 64);
BEGIN
  FOR v_row IN
    SELECT * FROM audit.audit_log WHERE aqueduct_id IS NOT DISTINCT FROM p_aqueduct ORDER BY id
  LOOP
    IF v_row.prev_hash <> v_prev
       OR v_row.row_hash <> encode(digest(v_prev || audit.audit_row_canonical(
            v_row.id, v_row.occurred_at, v_row.actor_type, v_row.actor_id, v_row.action,
            v_row.entity_type, v_row.entity_id, v_row.before_state, v_row.after_state,
            v_row.request_id, v_row.ip_hmac), 'sha256'), 'hex') THEN
      RETURN v_row.id;
    END IF;
    v_prev := v_row.row_hash;
  END LOOP;
  RETURN NULL;
END;
$$;

-- Daily anchor: the last row_hash of the day of every aqueduct, in the aqueduct's time zone.
CREATE FUNCTION audit.write_daily_anchor(p_date date)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, org, audit
AS $$
DECLARE
  v_previous text := current_setting('app.aqueduct_id', true);
  v_aqueduct record;
  v_head char(64);
  v_written integer := 0;
BEGIN
  FOR v_aqueduct IN SELECT id, timezone FROM org.aqueducts LOOP
    PERFORM set_config('app.aqueduct_id', v_aqueduct.id::text, true);
    SELECT row_hash INTO v_head
      FROM audit.audit_log
     WHERE aqueduct_id = v_aqueduct.id
       AND occurred_at < ((p_date + 1)::timestamp AT TIME ZONE v_aqueduct.timezone)
     ORDER BY id DESC
     LIMIT 1;
    IF v_head IS NOT NULL THEN
      INSERT INTO audit.audit_anchors (aqueduct_id, anchor_date, head_hash)
      VALUES (v_aqueduct.id, p_date, v_head)
      ON CONFLICT (aqueduct_id, anchor_date) DO NOTHING;
      IF FOUND THEN
        v_written := v_written + 1;
      END IF;
    END IF;
  END LOOP;
  PERFORM set_config('app.aqueduct_id', COALESCE(v_previous, ''), true);
  RETURN v_written;
END;
$$;

REVOKE ALL ON FUNCTION audit.audit_row_canonical(bigint, timestamptz, text, uuid, text, text, text, jsonb, jsonb, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.verify_chain(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.write_daily_anchor(date) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit.audit_row_canonical(bigint, timestamptz, text, uuid, text, text, text, jsonb, jsonb, text, text) TO caudal_app;
GRANT EXECUTE ON FUNCTION audit.verify_chain(uuid) TO caudal_app;
GRANT EXECUTE ON FUNCTION audit.write_daily_anchor(date) TO caudal_app;
