CREATE TABLE clinical_records (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  record_number TEXT NOT NULL UNIQUE,
  patient_id UUID NOT NULL,
  appointment_id UUID NOT NULL,
  doctor_id UUID NOT NULL,
  visit_at TIMESTAMPTZ NOT NULL,
  chief_complaint TEXT,
  medical_history TEXT,
  examination_notes TEXT,
  diagnosis TEXT,
  treatment_notes TEXT,
  follow_up_at TIMESTAMPTZ,
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SIGNED')),
  amends_record_id UUID REFERENCES clinical_records(id),
  amendment_reason TEXT,
  signed_at TIMESTAMPTZ,
  signed_by UUID,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((status='DRAFT' AND signed_at IS NULL AND signed_by IS NULL) OR (status='SIGNED' AND signed_at IS NOT NULL AND signed_by=doctor_id)),
  CHECK ((amends_record_id IS NULL AND amendment_reason IS NULL) OR (amends_record_id IS NOT NULL AND length(trim(amendment_reason))>0))
);
CREATE UNIQUE INDEX uq_original_record_per_appointment ON clinical_records(appointment_id) WHERE amends_record_id IS NULL;
CREATE INDEX idx_clinical_records_patient_history ON clinical_records(patient_id,visit_at DESC);
CREATE TABLE dental_record_teeth (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  record_id UUID NOT NULL REFERENCES clinical_records(id),
  tooth_number SMALLINT NOT NULL CHECK (tooth_number IN (11,12,13,14,15,16,17,18,21,22,23,24,25,26,27,28,31,32,33,34,35,36,37,38,41,42,43,44,45,46,47,48,51,52,53,54,55,61,62,63,64,65,71,72,73,74,75,81,82,83,84,85)),
  condition_code TEXT NOT NULL,
  surfaces TEXT[] NOT NULL DEFAULT '{}',
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(record_id,tooth_number)
);
CREATE TABLE performed_procedures (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL,
  record_id UUID NOT NULL REFERENCES clinical_records(id),
  doctor_id UUID NOT NULL,
  service_id UUID NOT NULL,
  title_snapshot TEXT NOT NULL,
  tooth_numbers INTEGER[] NOT NULL DEFAULT '{}',
  quantity NUMERIC(8,2) NOT NULL DEFAULT 1 CHECK (quantity>0),
  unit_price NUMERIC(15,2) NOT NULL CHECK (unit_price>=0),
  discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (discount_amount>=0),
  net_amount NUMERIC(15,2) GENERATED ALWAYS AS (quantity*unit_price-discount_amount) STORED,
  performed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  notes TEXT,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (discount_amount<=quantity*unit_price)
);
CREATE TABLE prescriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  prescription_number TEXT NOT NULL UNIQUE,
  patient_id UUID NOT NULL,
  record_id UUID NOT NULL REFERENCES clinical_records(id),
  doctor_id UUID NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  diagnosis_snapshot TEXT,
  notes TEXT,
  status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ISSUED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE prescription_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  prescription_id UUID NOT NULL REFERENCES prescriptions(id),
  medication_name TEXT NOT NULL,
  dosage TEXT NOT NULL,
  route TEXT NOT NULL,
  frequency TEXT NOT NULL,
  duration_days SMALLINT NOT NULL CHECK(duration_days>0),
  quantity TEXT NOT NULL,
  instructions TEXT
);
CREATE TABLE patient_files (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL,
  record_id UUID NOT NULL REFERENCES clinical_records(id),
  file_kind TEXT NOT NULL,
  object_key TEXT NOT NULL UNIQUE,
  original_filename TEXT NOT NULL,
  mime_type TEXT NOT NULL,
  byte_size BIGINT NOT NULL CHECK(byte_size>0 AND byte_size<=20971520),
  checksum_sha256 TEXT NOT NULL,
  uploaded_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE audit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_user_id UUID, actor_label TEXT NOT NULL, action TEXT NOT NULL, entity_type TEXT NOT NULL,
  entity_id UUID NOT NULL, request_id TEXT NOT NULL, metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE TABLE outbox_events (
  event_id UUID PRIMARY KEY, event_type TEXT NOT NULL, schema_version SMALLINT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL, aggregate_id UUID NOT NULL, aggregate_version BIGINT NOT NULL,
  correlation_id TEXT NOT NULL, payload JSONB NOT NULL, attempts SMALLINT NOT NULL DEFAULT 0,
  published_at TIMESTAMPTZ, dead_lettered_at TIMESTAMPTZ, last_error TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_pending ON outbox_events(created_at) WHERE published_at IS NULL;
CREATE TABLE processed_events(event_id UUID PRIMARY KEY,aggregate_id UUID NOT NULL,aggregate_version BIGINT NOT NULL,processed_at TIMESTAMPTZ NOT NULL DEFAULT now());

CREATE FUNCTION prevent_signed_record_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.status='SIGNED' THEN RAISE EXCEPTION 'Signed clinical records are immutable' USING ERRCODE='55000'; END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_clinical_records_immutable BEFORE UPDATE OR DELETE ON clinical_records FOR EACH ROW EXECUTE FUNCTION prevent_signed_record_mutation();

CREATE FUNCTION require_draft_clinical_parent() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_status TEXT; old_parent_status TEXT;
BEGIN
  IF TG_OP='UPDATE' OR TG_OP='DELETE' THEN
    SELECT status INTO old_parent_status FROM clinical_records WHERE id=OLD.record_id;
    IF old_parent_status IS DISTINCT FROM 'DRAFT' THEN RAISE EXCEPTION 'Children of signed records are immutable' USING ERRCODE='55000'; END IF;
  END IF;
  IF TG_OP='INSERT' OR TG_OP='UPDATE' THEN
    SELECT status INTO parent_status FROM clinical_records WHERE id=NEW.record_id;
    IF parent_status IS DISTINCT FROM 'DRAFT' THEN RAISE EXCEPTION 'Children can only be changed on a draft' USING ERRCODE='55000'; END IF;
    RETURN NEW;
  END IF;
  RETURN OLD;
END $$;
CREATE TRIGGER trg_record_teeth_draft BEFORE INSERT OR UPDATE OR DELETE ON dental_record_teeth FOR EACH ROW EXECUTE FUNCTION require_draft_clinical_parent();
CREATE TRIGGER trg_performed_procedures_draft BEFORE INSERT OR UPDATE OR DELETE ON performed_procedures FOR EACH ROW EXECUTE FUNCTION require_draft_clinical_parent();

CREATE FUNCTION require_signed_record_for_prescription() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_status TEXT;
BEGIN
  SELECT status INTO parent_status FROM clinical_records WHERE id=NEW.record_id AND doctor_id=NEW.doctor_id AND patient_id=NEW.patient_id;
  IF parent_status IS DISTINCT FROM 'SIGNED' THEN RAISE EXCEPTION 'Prescription requires a signed record' USING ERRCODE='23514'; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_prescription_signed_record BEFORE INSERT ON prescriptions FOR EACH ROW EXECUTE FUNCTION require_signed_record_for_prescription();
CREATE FUNCTION prevent_issued_prescription_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.status='ISSUED' THEN RAISE EXCEPTION 'Issued prescriptions are immutable' USING ERRCODE='55000'; END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_prescription_immutable BEFORE UPDATE OR DELETE ON prescriptions FOR EACH ROW EXECUTE FUNCTION prevent_issued_prescription_mutation();
CREATE FUNCTION require_draft_prescription_parent() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE prescription_status TEXT;
BEGIN
  IF TG_OP='UPDATE' OR TG_OP='DELETE' THEN
    SELECT status INTO prescription_status FROM prescriptions WHERE id=OLD.prescription_id;
    IF prescription_status IS DISTINCT FROM 'DRAFT' THEN RAISE EXCEPTION 'Issued prescription items are immutable' USING ERRCODE='55000'; END IF;
  END IF;
  IF TG_OP='INSERT' OR TG_OP='UPDATE' THEN
    SELECT status INTO prescription_status FROM prescriptions WHERE id=NEW.prescription_id;
    IF prescription_status IS DISTINCT FROM 'DRAFT' THEN RAISE EXCEPTION 'Prescription items can only be changed before issue' USING ERRCODE='55000'; END IF;
    RETURN NEW;
  END IF;
  RETURN OLD;
END $$;
CREATE TRIGGER trg_prescription_items_immutable BEFORE INSERT OR UPDATE OR DELETE ON prescription_items FOR EACH ROW EXECUTE FUNCTION require_draft_prescription_parent();

