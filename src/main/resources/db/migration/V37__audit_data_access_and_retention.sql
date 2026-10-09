-- V37: audit.data_access_log, audit.retention_policies.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE audit.data_access_log (
  id                         bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  aqueduct_id                uuid NOT NULL,
  user_id                    uuid NOT NULL,
  resource                   varchar(60) NOT NULL,
  resource_id                varchar(60),
  summary_share_id           uuid,
  accessed_at                timestamptz NOT NULL DEFAULT now(),
  ip_hmac                    varchar(64),
  CONSTRAINT data_access_log_pk PRIMARY KEY (id),
  CONSTRAINT data_access_log_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE audit.data_access_log IS 'Quién vio o exportó datos sensibles o autorizados.';
COMMENT ON COLUMN audit.data_access_log.id IS '[I] Identificador.';
COMMENT ON COLUMN audit.data_access_log.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN audit.data_access_log.user_id IS '[I] Usuario.';
COMMENT ON COLUMN audit.data_access_log.resource IS '[I] Recurso (MINUTES_PDF, SUMMARY, USER_LIST, AUDIT_LOG).';
COMMENT ON COLUMN audit.data_access_log.resource_id IS '[I] Identificador.';
COMMENT ON COLUMN audit.data_access_log.summary_share_id IS '[I] Autorización usada.';
COMMENT ON COLUMN audit.data_access_log.accessed_at IS '[I] Momento.';
COMMENT ON COLUMN audit.data_access_log.ip_hmac IS '[C] HMAC de la IP.';

ALTER TABLE audit.data_access_log ADD CONSTRAINT data_access_log_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE audit.data_access_log ADD CONSTRAINT data_access_log_user_id_fk
  FOREIGN KEY (user_id) REFERENCES iam.users (id) ON DELETE RESTRICT;
ALTER TABLE audit.data_access_log ADD CONSTRAINT data_access_log_summary_share_id_fk
  FOREIGN KEY (summary_share_id, aqueduct_id) REFERENCES reporting.summary_shares (id, aqueduct_id) ON DELETE RESTRICT;

CREATE INDEX data_access_log_aqueduct_id_idx ON audit.data_access_log (aqueduct_id);
CREATE INDEX data_access_log_user_id_idx ON audit.data_access_log (user_id);
CREATE INDEX data_access_log_summary_share_id_idx ON audit.data_access_log (summary_share_id);
CREATE INDEX data_access_log_time_idx ON audit.data_access_log (aqueduct_id, accessed_at DESC);

REVOKE ALL ON audit.data_access_log FROM PUBLIC;
GRANT SELECT, INSERT ON audit.data_access_log TO caudal_app;
GRANT USAGE ON SEQUENCE audit.data_access_log_id_seq TO caudal_app;

CREATE TABLE audit.retention_policies (
  data_class                 varchar(40) NOT NULL,
  retention_days             integer NOT NULL,
  updated_by                 uuid,
  updated_at                 timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT retention_policies_pk PRIMARY KEY (data_class),
  CONSTRAINT retention_policies_data_class_values CHECK (data_class IN ('LOGIN_ATTEMPTS', 'DEVICE_NONCES', 'SECURITY_EVENTS', 'REFRESH_TOKENS', 'DATA_ACCESS_LOG', 'TELEMETRY_POINTS')),
  CONSTRAINT retention_policies_retention_days_range CHECK (retention_days > 0)
);

COMMENT ON TABLE audit.retention_policies IS 'Días de retención por tipo de dato (parámetro en BD, no en código).';
COMMENT ON COLUMN audit.retention_policies.data_class IS '[I] Tipo de dato.';
COMMENT ON COLUMN audit.retention_policies.retention_days IS '[I] Días.';
COMMENT ON COLUMN audit.retention_policies.updated_by IS '[I] Quién lo cambió.';
COMMENT ON COLUMN audit.retention_policies.updated_at IS '[I] Momento.';

ALTER TABLE audit.retention_policies ADD CONSTRAINT retention_policies_updated_by_fk
  FOREIGN KEY (updated_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX retention_policies_updated_by_idx ON audit.retention_policies (updated_by);

REVOKE ALL ON audit.retention_policies FROM PUBLIC;
GRANT SELECT ON audit.retention_policies TO caudal_app;
GRANT UPDATE (retention_days, updated_by, updated_at) ON audit.retention_policies TO caudal_app;

-- Retention purges (DB doc, section 6.1). The application cannot DELETE: these SECURITY DEFINER
-- functions are the only deletion path. Each one reads its days from audit.retention_policies,
-- writes a PURGE row in audit.audit_log first and deletes after. Tables with RLS are purged one
-- aqueduct at a time, setting app.aqueduct_id and restoring the caller's value at the end.

CREATE FUNCTION audit.retention_cutoff(p_class text)
RETURNS timestamptz
LANGUAGE sql
STABLE
SET search_path = pg_catalog, audit
AS $$
  SELECT now() - make_interval(days => retention_days)
    FROM audit.retention_policies
   WHERE data_class = p_class;
$$;

CREATE FUNCTION audit.log_purge(p_class text, p_cutoff timestamptz, p_rows bigint, p_aqueduct uuid)
RETURNS void
LANGUAGE sql
SET search_path = pg_catalog, audit
AS $$
  INSERT INTO audit.audit_log (aqueduct_id, actor_type, action, entity_type, entity_id, after_state)
  VALUES (p_aqueduct, 'SYSTEM', 'PURGE', p_class, to_char(p_cutoff AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
          jsonb_build_object('data_class', p_class, 'cutoff', p_cutoff, 'rows', p_rows));
$$;

CREATE FUNCTION audit.purge_login_attempts()
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, iam, audit
AS $$
DECLARE
  v_cutoff timestamptz := audit.retention_cutoff('LOGIN_ATTEMPTS');
  v_rows bigint;
BEGIN
  SELECT count(*) INTO v_rows FROM iam.login_attempts WHERE created_at < v_cutoff;
  PERFORM audit.log_purge('LOGIN_ATTEMPTS', v_cutoff, v_rows, NULL);
  DELETE FROM iam.login_attempts WHERE created_at < v_cutoff;
  RETURN v_rows;
END;
$$;

CREATE FUNCTION audit.purge_security_events()
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, iam, audit
AS $$
DECLARE
  v_cutoff timestamptz := audit.retention_cutoff('SECURITY_EVENTS');
  v_rows bigint;
BEGIN
  SELECT count(*) INTO v_rows FROM iam.security_events WHERE created_at < v_cutoff;
  PERFORM audit.log_purge('SECURITY_EVENTS', v_cutoff, v_rows, NULL);
  DELETE FROM iam.security_events WHERE created_at < v_cutoff;
  RETURN v_rows;
END;
$$;

CREATE FUNCTION audit.purge_device_nonces()
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, devices, audit
AS $$
DECLARE
  v_cutoff timestamptz := audit.retention_cutoff('DEVICE_NONCES');
  v_rows bigint;
BEGIN
  SELECT count(*) INTO v_rows FROM devices.device_nonces WHERE seen_at < v_cutoff;
  PERFORM audit.log_purge('DEVICE_NONCES', v_cutoff, v_rows, NULL);
  DELETE FROM devices.device_nonces WHERE seen_at < v_cutoff;
  RETURN v_rows;
END;
$$;

-- Revoked or expired sessions, counted from their expiry. A token still referenced by
-- replaced_by_id stays until the older token of its chain is purged (FK RESTRICT).
CREATE FUNCTION audit.purge_refresh_tokens()
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, iam, audit
AS $$
DECLARE
  v_cutoff timestamptz := audit.retention_cutoff('REFRESH_TOKENS');
  v_rows bigint;
BEGIN
  SELECT count(*) INTO v_rows
    FROM iam.refresh_tokens t
   WHERE t.expires_at < v_cutoff
     AND NOT EXISTS (SELECT 1 FROM iam.refresh_tokens n WHERE n.replaced_by_id = t.id);
  PERFORM audit.log_purge('REFRESH_TOKENS', v_cutoff, v_rows, NULL);
  DELETE FROM iam.refresh_tokens t
   WHERE t.expires_at < v_cutoff
     AND NOT EXISTS (SELECT 1 FROM iam.refresh_tokens n WHERE n.replaced_by_id = t.id);
  RETURN v_rows;
END;
$$;

CREATE FUNCTION audit.purge_data_access_log()
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, org, audit
AS $$
DECLARE
  v_cutoff timestamptz := audit.retention_cutoff('DATA_ACCESS_LOG');
  v_previous text := current_setting('app.aqueduct_id', true);
  v_aqueduct uuid;
  v_rows bigint;
  v_total bigint := 0;
BEGIN
  FOR v_aqueduct IN SELECT id FROM org.aqueducts LOOP
    PERFORM set_config('app.aqueduct_id', v_aqueduct::text, true);
    SELECT count(*) INTO v_rows FROM audit.data_access_log WHERE accessed_at < v_cutoff;
    IF v_rows > 0 THEN
      PERFORM audit.log_purge('DATA_ACCESS_LOG', v_cutoff, v_rows, v_aqueduct);
      DELETE FROM audit.data_access_log WHERE accessed_at < v_cutoff;
      v_total := v_total + v_rows;
    END IF;
  END LOOP;
  PERFORM set_config('app.aqueduct_id', COALESCE(v_previous, ''), true);
  RETURN v_total;
END;
$$;

-- Points already turned into readings stay in ops.readings.
CREATE FUNCTION audit.purge_telemetry_points()
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, org, devices, audit
AS $$
DECLARE
  v_cutoff timestamptz := audit.retention_cutoff('TELEMETRY_POINTS');
  v_previous text := current_setting('app.aqueduct_id', true);
  v_aqueduct uuid;
  v_rows bigint;
  v_total bigint := 0;
BEGIN
  FOR v_aqueduct IN SELECT id FROM org.aqueducts LOOP
    PERFORM set_config('app.aqueduct_id', v_aqueduct::text, true);
    SELECT count(*) INTO v_rows FROM devices.telemetry_points WHERE received_at < v_cutoff;
    IF v_rows > 0 THEN
      PERFORM audit.log_purge('TELEMETRY_POINTS', v_cutoff, v_rows, v_aqueduct);
      DELETE FROM devices.telemetry_points WHERE received_at < v_cutoff;
      v_total := v_total + v_rows;
    END IF;
  END LOOP;
  PERFORM set_config('app.aqueduct_id', COALESCE(v_previous, ''), true);
  RETURN v_total;
END;
$$;

REVOKE ALL ON FUNCTION audit.retention_cutoff(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.log_purge(text, timestamptz, bigint, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.purge_login_attempts() FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.purge_security_events() FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.purge_device_nonces() FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.purge_refresh_tokens() FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.purge_data_access_log() FROM PUBLIC;
REVOKE ALL ON FUNCTION audit.purge_telemetry_points() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit.purge_login_attempts() TO caudal_app;
GRANT EXECUTE ON FUNCTION audit.purge_security_events() TO caudal_app;
GRANT EXECUTE ON FUNCTION audit.purge_device_nonces() TO caudal_app;
GRANT EXECUTE ON FUNCTION audit.purge_refresh_tokens() TO caudal_app;
GRANT EXECUTE ON FUNCTION audit.purge_data_access_log() TO caudal_app;
GRANT EXECUTE ON FUNCTION audit.purge_telemetry_points() TO caudal_app;
