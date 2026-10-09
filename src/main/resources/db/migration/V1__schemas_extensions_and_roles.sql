-- V1: schemas, extensions and database roles (docs/Seguridad-de-la-base-de-datos.md, section 2).
-- The user that runs Flyway owns every object. The application and read-only roles are created
-- here without a password: the password is set outside git (Neon console or ALTER ROLE by the
-- operator) and never travels in a migration or a placeholder.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

DO $roles$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'caudal_app') THEN
    CREATE ROLE caudal_app LOGIN;
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'caudal_readonly') THEN
    CREATE ROLE caudal_readonly LOGIN;
  END IF;
END
$roles$;

-- Never superuser and never above RLS. CREATE ROLE already defaults to that; the check fails the
-- migration if a pre-existing role was given more power (changing those attributes needs a
-- superuser, which the managed database does not provide).
DO $check$
BEGIN
  IF EXISTS (SELECT FROM pg_roles
              WHERE rolname IN ('caudal_app', 'caudal_readonly')
                AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb OR rolreplication)) THEN
    RAISE EXCEPTION 'caudal_app and caudal_readonly must not have elevated attributes';
  END IF;
END
$check$;

-- Timeouts per role (section 2.1). They apply to sessions that log in with these roles.
ALTER ROLE caudal_app SET statement_timeout = '5s';
ALTER ROLE caudal_app SET lock_timeout = '3s';
ALTER ROLE caudal_app SET idle_in_transaction_session_timeout = '10s';
ALTER ROLE caudal_readonly SET statement_timeout = '15s';
ALTER ROLE caudal_readonly SET lock_timeout = '3s';
ALTER ROLE caudal_readonly SET idle_in_transaction_session_timeout = '10s';

-- Nobody but the owner creates objects in public.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA iam;
CREATE SCHEMA org;
CREATE SCHEMA ops;
CREATE SCHEMA reporting;
CREATE SCHEMA devices;
CREATE SCHEMA audit;
CREATE SCHEMA sim;

COMMENT ON SCHEMA iam IS 'Identidad, acceso y seguridad de las cuentas.';
COMMENT ON SCHEMA org IS 'Organización del acueducto y reglas versionadas de la Junta.';
COMMENT ON SCHEMA ops IS 'Operación diaria: lecturas, pronósticos, turnos, publicación, cierre e incidentes.';
COMMENT ON SCHEMA reporting IS 'Actas y resúmenes autorizados para entidades de apoyo.';
COMMENT ON SCHEMA devices IS 'Dispositivos de campo (reales a futuro, simulados por ahora).';
COMMENT ON SCHEMA audit IS 'Auditoría y retención.';
COMMENT ON SCHEMA sim IS 'Trazabilidad de los datos simulados.';

REVOKE ALL ON SCHEMA iam, org, ops, reporting, devices, audit, sim FROM PUBLIC;
GRANT USAGE ON SCHEMA iam, org, ops, reporting, devices, audit, sim TO caudal_app;
GRANT USAGE ON SCHEMA reporting TO caudal_readonly;

-- Owner check used by the append-only trigger and the RLS maintenance policies: true only when
-- the current user owns the schema objects (inside SECURITY DEFINER purge functions or the
-- migration login). Avoids writing the owner role name in the database.
CREATE FUNCTION audit.is_object_owner()
RETURNS boolean
LANGUAGE sql
STABLE
SET search_path = pg_catalog
AS $$
  SELECT current_user = (SELECT r.rolname
                           FROM pg_namespace n JOIN pg_roles r ON r.oid = n.nspowner
                          WHERE n.nspname = 'audit');
$$;

REVOKE ALL ON FUNCTION audit.is_object_owner() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit.is_object_owner() TO caudal_app;

-- Shared trigger function: keeps updated_at in sync on every UPDATE (attached in V39).
CREATE FUNCTION audit.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;
