CREATE TABLE treatment_plans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL,
  clinical_record_id UUID NOT NULL UNIQUE REFERENCES clinical_records(id),
  doctor_id UUID NOT NULL,
  title TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','APPROVED')),
  version BIGINT NOT NULL DEFAULT 0,
  approved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((status='DRAFT' AND approved_at IS NULL) OR (status='APPROVED' AND approved_at IS NOT NULL))
);

CREATE TABLE treatment_plan_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  plan_id UUID NOT NULL REFERENCES treatment_plans(id),
  sequence_no SMALLINT NOT NULL CHECK (sequence_no > 0),
  service_id UUID NOT NULL,
  title_snapshot TEXT NOT NULL,
  quantity NUMERIC(8,2) NOT NULL DEFAULT 1 CHECK (quantity > 0),
  unit_price NUMERIC(15,2) NOT NULL CHECK (unit_price >= 0),
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(plan_id, sequence_no)
);

CREATE FUNCTION prevent_approved_plan_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.status='APPROVED' THEN RAISE EXCEPTION 'Approved treatment plans are immutable' USING ERRCODE='55000'; END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_treatment_plan_immutable BEFORE UPDATE OR DELETE ON treatment_plans FOR EACH ROW EXECUTE FUNCTION prevent_approved_plan_mutation();

CREATE FUNCTION require_draft_plan_parent() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_status TEXT;
BEGIN
  IF TG_OP='UPDATE' OR TG_OP='DELETE' THEN
    SELECT status INTO parent_status FROM treatment_plans WHERE id=OLD.plan_id;
    IF parent_status IS DISTINCT FROM 'DRAFT' THEN RAISE EXCEPTION 'Items of an approved treatment plan are immutable' USING ERRCODE='55000'; END IF;
  END IF;
  IF TG_OP='INSERT' OR TG_OP='UPDATE' THEN
    SELECT status INTO parent_status FROM treatment_plans WHERE id=NEW.plan_id;
    IF parent_status IS DISTINCT FROM 'DRAFT' THEN RAISE EXCEPTION 'Items can only be changed on a draft treatment plan' USING ERRCODE='55000'; END IF;
    RETURN NEW;
  END IF;
  RETURN OLD;
END $$;
CREATE TRIGGER trg_treatment_plan_items_draft BEFORE INSERT OR UPDATE OR DELETE ON treatment_plan_items FOR EACH ROW EXECUTE FUNCTION require_draft_plan_parent();
