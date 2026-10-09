-- V28: ops.publications.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE ops.publications (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid NOT NULL,
  proposal_id                uuid NOT NULL,
  published_by               uuid NOT NULL,
  published_at               timestamptz NOT NULL DEFAULT now(),
  whatsapp_text              text NOT NULL,
  poster_sha256              char(64),
  CONSTRAINT publications_pk PRIMARY KEY (id),
  CONSTRAINT publications_proposal_id_uq UNIQUE (proposal_id),
  CONSTRAINT publications_id_aqueduct_uq UNIQUE (id, aqueduct_id)
);

COMMENT ON TABLE ops.publications IS 'Publicación de un horario aprobado.';
COMMENT ON COLUMN ops.publications.id IS '[I] Identificador.';
COMMENT ON COLUMN ops.publications.aqueduct_id IS '[I] Acueducto.';
COMMENT ON COLUMN ops.publications.proposal_id IS '[I] Propuesta.';
COMMENT ON COLUMN ops.publications.published_by IS '[C] Quién publicó.';
COMMENT ON COLUMN ops.publications.published_at IS '[P] Momento.';
COMMENT ON COLUMN ops.publications.whatsapp_text IS '[P] Mensaje generado (sin datos personales).';
COMMENT ON COLUMN ops.publications.poster_sha256 IS '[P] Hash del cartel PDF.';

ALTER TABLE ops.publications ADD CONSTRAINT publications_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;
ALTER TABLE ops.publications ADD CONSTRAINT publications_proposal_id_fk
  FOREIGN KEY (proposal_id, aqueduct_id) REFERENCES ops.schedule_proposals (id, aqueduct_id) ON DELETE RESTRICT;
ALTER TABLE ops.publications ADD CONSTRAINT publications_published_by_fk
  FOREIGN KEY (published_by) REFERENCES iam.users (id) ON DELETE RESTRICT;

CREATE INDEX publications_aqueduct_id_idx ON ops.publications (aqueduct_id);
CREATE INDEX publications_published_by_idx ON ops.publications (published_by);

REVOKE ALL ON ops.publications FROM PUBLIC;
GRANT SELECT, INSERT ON ops.publications TO caudal_app;
