-- V31: reporting.minutes, reporting.summary_shares.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE reporting.minutes (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  period_from                date NOT NULL,
  period_to                  date NOT NULL,
  status                     varchar(10) NOT NULL DEFAULT 'DRAFT',
  content                    jsonb NOT NULL,
  pdf_sha256                 char(64),
  audit_head_hash            char(64),
  generated_by               uuid NOT NULL,
  generated_at               timestamptz NOT NULL DEFAULT now(),
  finalized_by               uuid,
  finalized_at               timestamptz,
  CONSTRAINT minutes_pk PRIMARY KEY (id),
  CONSTRAINT minutes_period_to_rule CHECK (period_to >= period_from),
  CONSTRAINT minutes_status_values CHECK (status IN ('DRAFT', 'FINAL')),
  CONSTRAINT minutes_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE reporting.minutes IS 'Actas por periodo con secciones observado, estimado, inferido y confirmado.';
COMMENT ON COLUMN reporting.minutes.id IS '[I] Identificador.';
COMMENT ON COLUMN reporting.minutes.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN reporting.minutes.period_from IS '[I] Inicio del periodo.';
COMMENT ON COLUMN reporting.minutes.period_to IS '[I] Fin del periodo.';
COMMENT ON COLUMN reporting.minutes.status IS '[I] Estado (FINAL es inmutable).';
COMMENT ON COLUMN reporting.minutes.content IS '[C] Secciones generadas.';
COMMENT ON COLUMN reporting.minutes.pdf_sha256 IS '[I] Hash del PDF final.';
COMMENT ON COLUMN reporting.minutes.audit_head_hash IS '[I] Ancla de la cadena de auditoría.';
COMMENT ON COLUMN reporting.minutes.generated_by IS '[I] Quién la generó.';
COMMENT ON COLUMN reporting.minutes.generated_at IS '[I] Momento.';
COMMENT ON COLUMN reporting.minutes.finalized_by IS '[I] Quién la cerró.';
COMMENT ON COLUMN reporting.minutes.finalized_at IS '[I] Momento del cierre.';

ALTER TABLE reporting.minutes ADD CONSTRAINT minutes_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE reporting.minutes ADD CONSTRAINT minutes_generated_by_fk
  FOREIGN KEY (generated_by) REFERENCES iam.users (id) ON DELETE RESTRICT;
ALTER TABLE reporting.minutes ADD CONSTRAINT minutes_finalized_by_fk
  FOREIGN KEY (finalized_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX minutes_aqueduct_id_idx ON reporting.minutes (aqueduct_id);
CREATE INDEX minutes_generated_by_idx ON reporting.minutes (generated_by);
CREATE INDEX minutes_finalized_by_idx ON reporting.minutes (finalized_by);

REVOKE ALL ON reporting.minutes FROM PUBLIC;
GRANT SELECT, INSERT ON reporting.minutes TO caudal_app;
GRANT UPDATE (status, content, pdf_sha256, audit_head_hash, finalized_by, finalized_at) ON reporting.minutes TO caudal_app;

CREATE TABLE reporting.summary_shares (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  grantee_user_id            uuid NOT NULL,
  scope                      varchar(20) NOT NULL,
  period_from                date NOT NULL,
  period_to                  date NOT NULL,
  reason                     varchar(${reason_max}) NOT NULL,
  authorized_by              uuid NOT NULL,
  authorized_at              timestamptz NOT NULL DEFAULT now(),
  expires_at                 timestamptz NOT NULL,
  revoked_at                 timestamptz,
  revoked_by                 uuid,
  CONSTRAINT summary_shares_pk PRIMARY KEY (id),
  CONSTRAINT summary_shares_scope_values CHECK (scope IN ('MONTHLY_SUMMARY', 'MINUTES')),
  CONSTRAINT summary_shares_reason_length CHECK (char_length(reason) >= ${reason_min}),
  CONSTRAINT summary_shares_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE reporting.summary_shares IS 'Autorizaciones de la Junta para que una entidad vea resúmenes.';
COMMENT ON COLUMN reporting.summary_shares.id IS '[I] Identificador.';
COMMENT ON COLUMN reporting.summary_shares.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN reporting.summary_shares.grantee_user_id IS '[I] Usuario de la entidad (rol SUPPORT_ENTITY).';
COMMENT ON COLUMN reporting.summary_shares.scope IS '[I] Qué puede ver.';
COMMENT ON COLUMN reporting.summary_shares.period_from IS '[I] Desde.';
COMMENT ON COLUMN reporting.summary_shares.period_to IS '[I] Hasta.';
COMMENT ON COLUMN reporting.summary_shares.reason IS '[I] Motivo.';
COMMENT ON COLUMN reporting.summary_shares.authorized_by IS '[I] Quién autorizó.';
COMMENT ON COLUMN reporting.summary_shares.authorized_at IS '[I] Momento.';
COMMENT ON COLUMN reporting.summary_shares.expires_at IS '[I] Vencimiento obligatorio.';
COMMENT ON COLUMN reporting.summary_shares.revoked_at IS '[I] Revocación.';
COMMENT ON COLUMN reporting.summary_shares.revoked_by IS '[I] Quién revocó.';

ALTER TABLE reporting.summary_shares ADD CONSTRAINT summary_shares_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE reporting.summary_shares ADD CONSTRAINT summary_shares_grantee_user_id_fk
  FOREIGN KEY (grantee_user_id) REFERENCES iam.users (id) ON DELETE RESTRICT;
ALTER TABLE reporting.summary_shares ADD CONSTRAINT summary_shares_authorized_by_fk
  FOREIGN KEY (authorized_by) REFERENCES iam.users (id) ON DELETE RESTRICT;
ALTER TABLE reporting.summary_shares ADD CONSTRAINT summary_shares_revoked_by_fk
  FOREIGN KEY (revoked_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX summary_shares_aqueduct_id_idx ON reporting.summary_shares (aqueduct_id);
CREATE INDEX summary_shares_grantee_user_id_idx ON reporting.summary_shares (grantee_user_id);
CREATE INDEX summary_shares_authorized_by_idx ON reporting.summary_shares (authorized_by);
CREATE INDEX summary_shares_revoked_by_idx ON reporting.summary_shares (revoked_by);

REVOKE ALL ON reporting.summary_shares FROM PUBLIC;
GRANT SELECT, INSERT ON reporting.summary_shares TO caudal_app;
GRANT UPDATE (revoked_at, revoked_by) ON reporting.summary_shares TO caudal_app;

-- A FINAL minutes document never changes again (DB doc, section 8).
CREATE FUNCTION reporting.guard_final_minutes()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, reporting
AS $$
BEGIN
  IF OLD.status = 'FINAL' THEN
    RAISE EXCEPTION 'minutes_final_immutable'
      USING ERRCODE = 'integrity_constraint_violation',
            HINT = 'Un acta FINAL no se modifica ni se borra.';
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER minutes_final_immutable
  BEFORE UPDATE OR DELETE ON reporting.minutes
  FOR EACH ROW EXECUTE FUNCTION reporting.guard_final_minutes();
