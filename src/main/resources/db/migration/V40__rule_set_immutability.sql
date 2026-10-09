-- V40: versioned rules of the Board (DB doc, section 8; business rules, section 2).
-- A DRAFT can change freely and move to ACTIVE or DISCARDED. An ACTIVE version only changes its
-- status to SUPERSEDED and its valid_to. SUPERSEDED and DISCARDED versions never change. The child
-- tables (bands, sector settings, valve order, operating windows) only change while the parent is
-- a DRAFT.

CREATE FUNCTION org.guard_rule_set_update()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, org
AS $$
BEGIN
  IF OLD.status = 'DRAFT' THEN
    IF NEW.status NOT IN ('DRAFT', 'ACTIVE', 'DISCARDED') THEN
      RAISE EXCEPTION 'invalid_state_transition'
        USING ERRCODE = 'integrity_constraint_violation',
              HINT = format('%s -> %s', OLD.status, NEW.status);
    END IF;
    RETURN NEW;
  END IF;

  IF OLD.status = 'ACTIVE' AND NEW.status IN ('ACTIVE', 'SUPERSEDED')
     AND (to_jsonb(NEW) - ARRAY['status', 'valid_to'])
         = (to_jsonb(OLD) - ARRAY['status', 'valid_to']) THEN
    RETURN NEW;
  END IF;

  RAISE EXCEPTION 'rule_set_immutable'
    USING ERRCODE = 'integrity_constraint_violation',
          HINT = 'Las reglas activas no se pueden modificar. Crea un borrador nuevo.';
END;
$$;

CREATE TRIGGER rule_set_immutability
  BEFORE UPDATE ON org.rule_sets
  FOR EACH ROW EXECUTE FUNCTION org.guard_rule_set_update();

CREATE FUNCTION org.guard_rule_child()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog, org
AS $$
DECLARE
  v_rule_set uuid;
BEGIN
  IF TG_OP IN ('UPDATE', 'DELETE') THEN
    v_rule_set := OLD.rule_set_id;
    IF NOT EXISTS (SELECT 1 FROM org.rule_sets WHERE id = v_rule_set AND status = 'DRAFT') THEN
      RAISE EXCEPTION 'rule_set_immutable'
        USING ERRCODE = 'integrity_constraint_violation',
              HINT = 'Solo se modifican las reglas de un borrador.';
    END IF;
  END IF;
  IF TG_OP IN ('INSERT', 'UPDATE') THEN
    v_rule_set := NEW.rule_set_id;
    IF NOT EXISTS (SELECT 1 FROM org.rule_sets WHERE id = v_rule_set AND status = 'DRAFT') THEN
      RAISE EXCEPTION 'rule_set_immutable'
        USING ERRCODE = 'integrity_constraint_violation',
              HINT = 'Solo se modifican las reglas de un borrador.';
    END IF;
    RETURN NEW;
  END IF;
  RETURN OLD;
END;
$$;

DO $children$
DECLARE
  v_table text;
BEGIN
  FOREACH v_table IN ARRAY ARRAY[
    'org.rule_level_bands', 'org.rule_sector_settings',
    'org.rule_valve_orders', 'org.rule_operating_windows']
  LOOP
    EXECUTE format(
      'CREATE TRIGGER rule_child_draft_only BEFORE INSERT OR UPDATE OR DELETE ON %s '
      'FOR EACH ROW EXECUTE FUNCTION org.guard_rule_child()', v_table);
  END LOOP;
END
$children$;
