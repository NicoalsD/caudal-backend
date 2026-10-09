-- V16: org.catalog_items.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE org.catalog_items (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  aqueduct_id                uuid,
  catalog                    varchar(40) NOT NULL,
  code                       varchar(${catalog_code_max}) NOT NULL,
  label_es                   varchar(${catalog_label_max}) NOT NULL,
  sort_order                 smallint NOT NULL DEFAULT 0,
  is_active                  boolean NOT NULL DEFAULT true,
  CONSTRAINT catalog_items_pk PRIMARY KEY (id),
  CONSTRAINT catalog_items_catalog_values CHECK (catalog IN ('WATER_APPEARANCE', 'DAMAGE_CATEGORY')),
  CONSTRAINT catalog_items_code_length CHECK (char_length(code) >= ${catalog_code_min}),
  CONSTRAINT catalog_items_code_format CHECK (code ~ '${catalog_code_pattern}'),
  CONSTRAINT catalog_items_label_es_length CHECK (char_length(label_es) >= ${catalog_label_min})
);

COMMENT ON TABLE org.catalog_items IS 'Opciones de listas (aspecto del agua, categorías de daño) con etiqueta en español.';
COMMENT ON COLUMN org.catalog_items.id IS '[P] Identificador.';
COMMENT ON COLUMN org.catalog_items.aqueduct_id IS '[P] Nulo = catálogo global.';
COMMENT ON COLUMN org.catalog_items.catalog IS '[P] Catálogo.';
COMMENT ON COLUMN org.catalog_items.code IS '[P] Código en inglés.';
COMMENT ON COLUMN org.catalog_items.label_es IS '[P] Etiqueta visible.';
COMMENT ON COLUMN org.catalog_items.sort_order IS '[P] Orden.';
COMMENT ON COLUMN org.catalog_items.is_active IS '[P] Activo.';

ALTER TABLE org.catalog_items ADD CONSTRAINT catalog_items_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;

CREATE INDEX catalog_items_aqueduct_id_idx ON org.catalog_items (aqueduct_id);
ALTER TABLE org.catalog_items ADD CONSTRAINT catalog_items_code_uq UNIQUE NULLS NOT DISTINCT (aqueduct_id, catalog, code);

REVOKE ALL ON org.catalog_items FROM PUBLIC;
GRANT SELECT ON org.catalog_items TO caudal_app;
