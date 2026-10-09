-- V26: ops.schedule_items, ops.schedule_item_reasons.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE ops.schedule_items (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  proposal_id                uuid NOT NULL,
  sector_id                  uuid NOT NULL,
  sequence                   smallint NOT NULL,
  start_at                   timestamptz NOT NULL,
  end_at                     timestamptz NOT NULL,
  origin                     varchar(10) NOT NULL DEFAULT 'PROPOSED',
  CONSTRAINT schedule_items_pk PRIMARY KEY (id),
  CONSTRAINT schedule_items_sequence_range CHECK (sequence > 0),
  CONSTRAINT schedule_items_end_at_rule CHECK (end_at > start_at),
  CONSTRAINT schedule_items_origin_values CHECK (origin IN ('PROPOSED', 'MODIFIED', 'ADDED')),
  CONSTRAINT schedule_items_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.schedule_items IS 'Turnos por sector. Sin solapes (EXCLUDE con tstzrange).';
COMMENT ON COLUMN ops.schedule_items.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.schedule_items.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.schedule_items.proposal_id IS '[I] Propuesta.';
COMMENT ON COLUMN ops.schedule_items.sector_id IS '[P] Sector.';
COMMENT ON COLUMN ops.schedule_items.sequence IS '[P] Orden.';
COMMENT ON COLUMN ops.schedule_items.start_at IS '[P] Inicio.';
COMMENT ON COLUMN ops.schedule_items.end_at IS '[P] Fin.';
COMMENT ON COLUMN ops.schedule_items.origin IS '[I] Origen.';

ALTER TABLE ops.schedule_items ADD CONSTRAINT schedule_items_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.schedule_items ADD CONSTRAINT schedule_items_proposal_id_fk
  FOREIGN KEY (proposal_id, aqueduct_id) REFERENCES ops.schedule_proposals (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.schedule_items ADD CONSTRAINT schedule_items_sector_id_fk
  FOREIGN KEY (sector_id, aqueduct_id) REFERENCES org.sectors (id, aqueduct_id) ON DELETE RESTRICT;

CREATE INDEX schedule_items_aqueduct_id_idx ON ops.schedule_items (aqueduct_id);
CREATE INDEX schedule_items_proposal_id_idx ON ops.schedule_items (proposal_id);
CREATE INDEX schedule_items_sector_id_idx ON ops.schedule_items (sector_id);
ALTER TABLE ops.schedule_items ADD CONSTRAINT schedule_items_sequence_uq UNIQUE (proposal_id, sequence);
ALTER TABLE ops.schedule_items ADD CONSTRAINT schedule_items_no_overlap
  EXCLUDE USING gist (proposal_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&);

REVOKE ALL ON ops.schedule_items FROM PUBLIC;
GRANT SELECT, INSERT, DELETE ON ops.schedule_items TO caudal_app;

CREATE TABLE ops.schedule_item_reasons (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  item_id                    uuid NOT NULL,
  reason_code                varchar(30) NOT NULL,
  params                     jsonb NOT NULL DEFAULT '{}',
  sort_order                 smallint NOT NULL DEFAULT 0,
  CONSTRAINT schedule_item_reasons_pk PRIMARY KEY (id),
  CONSTRAINT schedule_item_reasons_reason_code_values CHECK (reason_code IN ('PRIORITY_SECTOR', 'HOURS_WITHOUT_SERVICE', 'TANK_BAND', 'FORECAST_RANGE', 'RESERVE_GUARD', 'BOARD_CHANGE')),
  CONSTRAINT schedule_item_reasons_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.schedule_item_reasons IS 'Explicaciones estructuradas de cada turno (código y parámetros).';
COMMENT ON COLUMN ops.schedule_item_reasons.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.schedule_item_reasons.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.schedule_item_reasons.item_id IS '[I] Turno.';
COMMENT ON COLUMN ops.schedule_item_reasons.reason_code IS '[P] Código.';
COMMENT ON COLUMN ops.schedule_item_reasons.params IS '[P] Parámetros (sector, horas, nivel).';
COMMENT ON COLUMN ops.schedule_item_reasons.sort_order IS '[P] Orden.';

ALTER TABLE ops.schedule_item_reasons ADD CONSTRAINT schedule_item_reasons_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.schedule_item_reasons ADD CONSTRAINT schedule_item_reasons_item_id_fk
  FOREIGN KEY (item_id, aqueduct_id) REFERENCES ops.schedule_items (id, aqueduct_id) ON DELETE RESTRICT;

CREATE INDEX schedule_item_reasons_aqueduct_id_idx ON ops.schedule_item_reasons (aqueduct_id);
CREATE INDEX schedule_item_reasons_item_id_idx ON ops.schedule_item_reasons (item_id);

REVOKE ALL ON ops.schedule_item_reasons FROM PUBLIC;
GRANT SELECT, INSERT, DELETE ON ops.schedule_item_reasons TO caudal_app;

-- Shifts can only be removed while the proposal is not decided yet (DB doc, section 8).
CREATE FUNCTION ops.guard_schedule_delete()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, ops
AS $$
DECLARE
  v_status text;
BEGIN
  IF TG_TABLE_NAME = 'schedule_items' THEN
    SELECT p.status INTO v_status FROM ops.schedule_proposals p WHERE p.id = OLD.proposal_id;
  ELSE
    SELECT p.status INTO v_status
      FROM ops.schedule_items i JOIN ops.schedule_proposals p ON p.id = i.proposal_id
     WHERE i.id = OLD.item_id;
  END IF;
  IF v_status IS DISTINCT FROM 'DRAFT' AND v_status IS DISTINCT FROM 'PENDING_REVIEW' THEN
    RAISE EXCEPTION 'schedule_locked'
      USING ERRCODE = 'integrity_constraint_violation',
            HINT = 'Solo se borran turnos de propuestas en DRAFT o PENDING_REVIEW.';
  END IF;
  RETURN OLD;
END;
$$;

CREATE TRIGGER schedule_items_draft_only_delete
  BEFORE DELETE ON ops.schedule_items
  FOR EACH ROW EXECUTE FUNCTION ops.guard_schedule_delete();

CREATE TRIGGER schedule_item_reasons_draft_only_delete
  BEFORE DELETE ON ops.schedule_item_reasons
  FOR EACH ROW EXECUTE FUNCTION ops.guard_schedule_delete();
