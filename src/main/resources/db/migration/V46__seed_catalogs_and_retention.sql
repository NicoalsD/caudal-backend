-- V46: global catalogs (aqueduct_id NULL) and default retention (facts, section 21).
-- Retention days are parameters in the database; PROJECT_TEAM changes them through the API.

INSERT INTO org.catalog_items (aqueduct_id, catalog, code, label_es, sort_order) VALUES
  (NULL, 'WATER_APPEARANCE', 'NORMAL', 'Normal', 1),
  (NULL, 'WATER_APPEARANCE', 'MUDDY', 'Con barro', 2),
  (NULL, 'WATER_APPEARANCE', 'TURBID', 'Turbia', 3),
  (NULL, 'WATER_APPEARANCE', 'NOT_OBSERVED', 'Sin observar', 4),
  (NULL, 'DAMAGE_CATEGORY', 'LEAK', 'Fuga', 1),
  (NULL, 'DAMAGE_CATEGORY', 'BROKEN_PIPE', 'Tubo roto', 2),
  (NULL, 'DAMAGE_CATEGORY', 'NO_WATER', 'Sin agua', 3),
  (NULL, 'DAMAGE_CATEGORY', 'DIRTY_WATER', 'Agua sucia', 4),
  (NULL, 'DAMAGE_CATEGORY', 'VALVE_FAILURE', 'Falla de válvula', 5),
  (NULL, 'DAMAGE_CATEGORY', 'OTHER', 'Otro', 6);

INSERT INTO audit.retention_policies (data_class, retention_days) VALUES
  ('LOGIN_ATTEMPTS', 90),
  ('DEVICE_NONCES', 1),
  ('SECURITY_EVENTS', 365),
  ('REFRESH_TOKENS', 30),
  ('DATA_ACCESS_LOG', 365),
  ('TELEMETRY_POINTS', 730);
