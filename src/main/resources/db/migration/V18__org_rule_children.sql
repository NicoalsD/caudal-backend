-- V18: org.rule_level_bands, org.rule_sector_settings, org.rule_valve_orders, org.rule_operating_windows.
-- Generated from the schema specification (data dictionary); lengths and ASCII
-- patterns come from FieldLimits through Flyway placeholders.

CREATE TABLE org.rule_level_bands (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  rule_set_id                uuid NOT NULL,
  band                       varchar(10) NOT NULL,
  min_level                  numeric(6,2) NOT NULL,
  max_level                  numeric(6,2) NOT NULL,
  daily_service_hours        numeric(4,2) NOT NULL,
  priority_only              boolean NOT NULL DEFAULT false,
  CONSTRAINT rule_level_bands_pk PRIMARY KEY (id),
  CONSTRAINT rule_level_bands_band_values CHECK (band IN ('HIGH', 'LOW', 'CRITICAL')),
  CONSTRAINT rule_level_bands_max_level_rule CHECK (max_level > min_level),
  CONSTRAINT rule_level_bands_daily_service_hours_range CHECK (daily_service_hours >= 0 AND daily_service_hours <= ${hours_per_day})
);

COMMENT ON TABLE org.rule_level_bands IS 'Bandas de nivel con horas de servicio. Sin solapes (EXCLUDE con numrange).';
COMMENT ON COLUMN org.rule_level_bands.id IS '[I] Identificador.';
COMMENT ON COLUMN org.rule_level_bands.rule_set_id IS '[I] Versión de reglas.';
COMMENT ON COLUMN org.rule_level_bands.band IS '[P] Banda.';
COMMENT ON COLUMN org.rule_level_bands.min_level IS '[P] Nivel mínimo (incluido).';
COMMENT ON COLUMN org.rule_level_bands.max_level IS '[P] Nivel máximo (excluido, salvo la banda superior, que incluye gauge_max).';
COMMENT ON COLUMN org.rule_level_bands.daily_service_hours IS '[P] Horas de servicio al día.';
COMMENT ON COLUMN org.rule_level_bands.priority_only IS '[P] Solo sectores prioritarios.';

ALTER TABLE org.rule_level_bands ADD CONSTRAINT rule_level_bands_rule_set_id_fk
  FOREIGN KEY (rule_set_id) REFERENCES org.rule_sets (id) ON DELETE RESTRICT;

CREATE INDEX rule_level_bands_rule_set_id_idx ON org.rule_level_bands (rule_set_id);
ALTER TABLE org.rule_level_bands ADD CONSTRAINT rule_level_bands_band_uq UNIQUE (rule_set_id, band);
ALTER TABLE org.rule_level_bands ADD CONSTRAINT rule_level_bands_no_overlap
  EXCLUDE USING gist (rule_set_id WITH =, numrange(min_level, max_level, '[)') WITH &&);

REVOKE ALL ON org.rule_level_bands FROM PUBLIC;
GRANT SELECT, INSERT, DELETE, UPDATE ON org.rule_level_bands TO caudal_app;

CREATE TABLE org.rule_sector_settings (
  rule_set_id                uuid,
  sector_id                  uuid,
  is_included                boolean NOT NULL DEFAULT true,
  is_priority                boolean NOT NULL DEFAULT false,
  priority_rank              smallint,
  CONSTRAINT rule_sector_settings_pk PRIMARY KEY (rule_set_id, sector_id),
  CONSTRAINT rule_sector_settings_priority_rank_range CHECK (priority_rank > 0)
);

COMMENT ON TABLE org.rule_sector_settings IS 'Prioridad e inclusión de cada sector por versión de reglas.';
COMMENT ON COLUMN org.rule_sector_settings.rule_set_id IS '[I] Versión.';
COMMENT ON COLUMN org.rule_sector_settings.sector_id IS '[I] Sector.';
COMMENT ON COLUMN org.rule_sector_settings.is_included IS '[P] Recibe turnos.';
COMMENT ON COLUMN org.rule_sector_settings.is_priority IS '[P] Prioritario (por ejemplo, la escuela).';
COMMENT ON COLUMN org.rule_sector_settings.priority_rank IS '[P] Orden entre prioritarios.';

ALTER TABLE org.rule_sector_settings ADD CONSTRAINT rule_sector_settings_rule_set_id_fk
  FOREIGN KEY (rule_set_id) REFERENCES org.rule_sets (id) ON DELETE RESTRICT;
ALTER TABLE org.rule_sector_settings ADD CONSTRAINT rule_sector_settings_sector_id_fk
  FOREIGN KEY (sector_id) REFERENCES org.sectors (id) ON DELETE RESTRICT;

CREATE INDEX rule_sector_settings_sector_id_idx ON org.rule_sector_settings (sector_id);

REVOKE ALL ON org.rule_sector_settings FROM PUBLIC;
GRANT SELECT, INSERT, DELETE, UPDATE ON org.rule_sector_settings TO caudal_app;

CREATE TABLE org.rule_valve_orders (
  rule_set_id                uuid,
  valve_id                   uuid,
  sequence_order             smallint NOT NULL,
  CONSTRAINT rule_valve_orders_pk PRIMARY KEY (rule_set_id, valve_id),
  CONSTRAINT rule_valve_orders_sequence_order_range CHECK (sequence_order > 0)
);

COMMENT ON TABLE org.rule_valve_orders IS 'Orden de apertura de las válvulas por versión de reglas.';
COMMENT ON COLUMN org.rule_valve_orders.rule_set_id IS '[I] Versión.';
COMMENT ON COLUMN org.rule_valve_orders.valve_id IS '[I] Válvula.';
COMMENT ON COLUMN org.rule_valve_orders.sequence_order IS '[P] Posición en la secuencia.';

ALTER TABLE org.rule_valve_orders ADD CONSTRAINT rule_valve_orders_rule_set_id_fk
  FOREIGN KEY (rule_set_id) REFERENCES org.rule_sets (id) ON DELETE RESTRICT;
ALTER TABLE org.rule_valve_orders ADD CONSTRAINT rule_valve_orders_valve_id_fk
  FOREIGN KEY (valve_id) REFERENCES org.valves (id) ON DELETE RESTRICT;

CREATE INDEX rule_valve_orders_valve_id_idx ON org.rule_valve_orders (valve_id);
ALTER TABLE org.rule_valve_orders ADD CONSTRAINT rule_valve_orders_sequence_uq UNIQUE (rule_set_id, sequence_order);

REVOKE ALL ON org.rule_valve_orders FROM PUBLIC;
GRANT SELECT, INSERT, DELETE, UPDATE ON org.rule_valve_orders TO caudal_app;

CREATE TABLE org.rule_operating_windows (
  id                         uuid NOT NULL DEFAULT uuidv7(),
  rule_set_id                uuid NOT NULL,
  day_of_week                smallint,
  start_time                 time NOT NULL,
  end_time                   time NOT NULL,
  CONSTRAINT rule_operating_windows_pk PRIMARY KEY (id),
  CONSTRAINT rule_operating_windows_day_of_week_range CHECK (day_of_week BETWEEN 1 AND ${days_per_week}),
  CONSTRAINT rule_operating_windows_end_time_rule CHECK (end_time > start_time)
);

COMMENT ON TABLE org.rule_operating_windows IS 'Horarios en los que se puede operar, por versión de reglas.';
COMMENT ON COLUMN org.rule_operating_windows.id IS '[I] Identificador.';
COMMENT ON COLUMN org.rule_operating_windows.rule_set_id IS '[I] Versión.';
COMMENT ON COLUMN org.rule_operating_windows.day_of_week IS '[P] Día (nulo = todos).';
COMMENT ON COLUMN org.rule_operating_windows.start_time IS '[P] Inicio.';
COMMENT ON COLUMN org.rule_operating_windows.end_time IS '[P] Fin.';

ALTER TABLE org.rule_operating_windows ADD CONSTRAINT rule_operating_windows_rule_set_id_fk
  FOREIGN KEY (rule_set_id) REFERENCES org.rule_sets (id) ON DELETE RESTRICT;

CREATE INDEX rule_operating_windows_rule_set_id_idx ON org.rule_operating_windows (rule_set_id);

REVOKE ALL ON org.rule_operating_windows FROM PUBLIC;
GRANT SELECT, INSERT, DELETE, UPDATE ON org.rule_operating_windows TO caudal_app;
