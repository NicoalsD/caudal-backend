-- V50: public tracking of damage reports (docs/API.md, section 15.5).
-- The tracking code comes without an aqueduct, so the application cannot fix app.aqueduct_id
-- before the lookup. This SECURITY DEFINER function runs as the owner, finds one incident by the
-- hash of its code and returns only what the public may see: never the description, the place,
-- the sector or the HMAC of the address. The policies let only the owner read across aqueducts.

CREATE POLICY public_tracking ON ops.incidents
  FOR SELECT
  USING (audit.is_object_owner());

CREATE POLICY public_tracking ON ops.incident_status_history
  FOR SELECT
  USING (audit.is_object_owner());

CREATE FUNCTION ops.find_public_report(p_tracking_code_hash char(64))
RETURNS TABLE (
  aqueduct_id   uuid,
  status        varchar(12),
  category_code varchar(${catalog_code_max}),
  reported_at   timestamptz,
  updated_at    timestamptz
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, ops
AS $$
  SELECT i.aqueduct_id, i.status, i.category_code, i.reported_at,
         COALESCE((SELECT max(h.changed_at) FROM ops.incident_status_history h
                    WHERE h.incident_id = i.id), i.reported_at)
    FROM ops.incidents i
   WHERE i.tracking_code_hash = p_tracking_code_hash;
$$;

REVOKE ALL ON FUNCTION ops.find_public_report(char) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ops.find_public_report(char) TO caudal_app;
