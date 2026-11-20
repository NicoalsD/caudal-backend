-- V56: login B2B por institución, primera ronda (docs/API.md 2.1-2.2; Actores-y-permisos.md 3).
-- Mientras no se renombre aqueduct a institution, "institución" = org.aqueducts.
-- org.aqueducts e iam.users no tienen RLS: el directorio público lee org.aqueducts con caudal_app
-- sin función SECURITY DEFINER.

-- 0. Roles nuevos. Van primero: el remapeo de membresías del paso 1 los referencia por FK. La restricción de códigos de V3 se rehace con todos los roles.
ALTER TABLE iam.roles DROP CONSTRAINT roles_code_values;
ALTER TABLE iam.roles ADD CONSTRAINT roles_code_values CHECK (code IN (
  'BOARD_ADMIN', 'BOARD_MEMBER', 'OPERATOR', 'PROJECT_TEAM', 'SUPPORT_ENTITY',
  'INSTITUTION_ADMIN', 'VIEWER'));

INSERT INTO iam.roles (code, label_es, description_es) VALUES
  ('INSTITUTION_ADMIN', 'Administrador de la institución',
   'Administra usuarios, reglas, decisiones, horarios, actas y autorizaciones de su institución.'),
  ('VIEWER', 'Lector',
   'Consulta el estado, las lecturas, el horario, los incidentes, las actas y el asistente, sin cambiar nada.');

-- INSTITUTION_ADMIN recibe exactamente los permisos que BOARD_ADMIN tiene al aplicar esta migración.
INSERT INTO iam.role_permissions (role_code, permission_code)
SELECT 'INSTITUTION_ADMIN', rp.permission_code
  FROM iam.role_permissions rp
 WHERE rp.role_code = 'BOARD_ADMIN';

-- VIEWER: solo permisos de lectura que ya existen.
INSERT INTO iam.role_permissions (role_code, permission_code)
SELECT 'VIEWER', p.code
  FROM iam.permissions p
 WHERE p.code IN ('NETWORK_VIEW', 'STATUS_VIEW', 'READING_VIEW', 'SCHEDULE_VIEW',
                  'INCIDENT_VIEW', 'MINUTES_VIEW', 'REPORT_VIEW', 'ASSISTANT_USE');

-- 1. Institución de la cuenta. NULL queda reservado para el superadmin.
ALTER TABLE iam.users ADD COLUMN aqueduct_id uuid NULL;

COMMENT ON COLUMN iam.users.aqueduct_id IS '[I] Institución de la cuenta; NULL solo para el superadmin.';

ALTER TABLE iam.users ADD CONSTRAINT users_aqueduct_id_fk
  FOREIGN KEY (aqueduct_id) REFERENCES org.aqueducts (id) ON DELETE RESTRICT;

CREATE INDEX users_aqueduct_id_idx ON iam.users (aqueduct_id);

-- Relleno: la membresía activa más antigua de cada usuario. Si no tiene ninguna activa, la más
-- antigua que tenga. Las membresías tienen RLS por acueducto: se leen y se remapean una institución
-- a la vez, con app.aqueduct_id fijado dentro de la transacción (mismo patrón que V37 y V47).
CREATE TEMPORARY TABLE v56_memberships (
  user_id uuid NOT NULL,
  aqueduct_id uuid NOT NULL,
  is_active boolean NOT NULL,
  valid_from timestamptz NOT NULL
) ON COMMIT DROP;

DO $backfill$
DECLARE
  v_previous text := current_setting('app.aqueduct_id', true);
  v_aqueduct record;
BEGIN
  FOR v_aqueduct IN SELECT id FROM org.aqueducts ORDER BY id LOOP
    PERFORM set_config('app.aqueduct_id', v_aqueduct.id::text, true);

    INSERT INTO v56_memberships (user_id, aqueduct_id, is_active, valid_from)
    SELECT m.user_id, m.aqueduct_id,
           (m.valid_from <= now() AND (m.valid_to IS NULL OR m.valid_to > now())),
           m.valid_from
      FROM iam.memberships m
     WHERE m.aqueduct_id = v_aqueduct.id;

    -- Remapeo de roles (no se borran los roles viejos). Solo cambia role_code: el índice
    -- memberships_one_open es (user_id, aqueduct_id) y no se altera, así que no hay choque.
    UPDATE iam.memberships
       SET role_code = CASE role_code
             WHEN 'BOARD_ADMIN' THEN 'INSTITUTION_ADMIN'
             WHEN 'BOARD_MEMBER' THEN 'INSTITUTION_ADMIN'
             WHEN 'PROJECT_TEAM' THEN 'VIEWER'
             WHEN 'SUPPORT_ENTITY' THEN 'VIEWER'
             ELSE role_code
           END
     WHERE aqueduct_id = v_aqueduct.id
       AND role_code IN ('BOARD_ADMIN', 'BOARD_MEMBER', 'PROJECT_TEAM', 'SUPPORT_ENTITY');
  END LOOP;

  UPDATE iam.users u
     SET aqueduct_id = f.aqueduct_id
    FROM (SELECT DISTINCT ON (user_id) user_id, aqueduct_id
            FROM v56_memberships
           ORDER BY user_id, is_active DESC, valid_from, aqueduct_id) f
   WHERE u.id = f.user_id;

  PERFORM set_config('app.aqueduct_id', COALESCE(v_previous, ''), true);
END
$backfill$;

-- 2. Usuario único por institución. Dos instituciones pueden tener un usuario "admin".
ALTER TABLE iam.users DROP CONSTRAINT users_username_uq;

-- Con aqueduct_id NULL, la unicidad de (aqueduct_id, username) no aplica (NULL no es igual a NULL),
-- por eso el índice parcial sobre username cubre al superadmin.
CREATE UNIQUE INDEX users_aqueduct_username_uq ON iam.users (aqueduct_id, username);
CREATE UNIQUE INDEX users_platform_username_uq ON iam.users (username) WHERE aqueduct_id IS NULL;

-- 4. Privilegios de columna: la aplicación lee aqueduct_id para resolver el login por institución.
-- INSERT ya es de tabla completa (V44). Sin UPDATE nuevo: la institución no cambia.
GRANT SELECT (aqueduct_id) ON iam.users TO caudal_app;
