CREATE TABLE patients (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_code TEXT NOT NULL UNIQUE,
  full_name TEXT NOT NULL,
  phone TEXT NOT NULL,
  email TEXT,
  gender TEXT CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER')),
  date_of_birth DATE,
  address TEXT,
  occupation TEXT,
  emergency_contact TEXT,
  emergency_phone TEXT,
  blood_type TEXT CHECK (blood_type IS NULL OR blood_type IN ('A','B','AB','O')),
  rh_factor TEXT CHECK (rh_factor IS NULL OR rh_factor IN ('POSITIVE','NEGATIVE')),
  allergies TEXT,
  medical_history TEXT,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ,
  created_by UUID
);
CREATE INDEX idx_patients_name ON patients(lower(full_name));
CREATE INDEX idx_patients_phone ON patients(phone);
CREATE TABLE patient_account_links (
  patient_id UUID PRIMARY KEY REFERENCES patients(id),
  user_id UUID NOT NULL UNIQUE,
  linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  linked_by UUID
);
CREATE TABLE patient_consents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID NOT NULL REFERENCES patients(id),
  consent_type TEXT NOT NULL CHECK (consent_type IN ('DATA_PROCESSING','MARKETING_ZALO','MARKETING_SMS','MARKETING_EMAIL','PHOTO_USE','GENERAL_TREATMENT')),
  status TEXT NOT NULL CHECK (status IN ('GRANTED','REVOKED')),
  granted_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  channel TEXT,
  policy_version TEXT,
  evidence_key TEXT,
  recorded_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((status='GRANTED' AND revoked_at IS NULL) OR (status='REVOKED' AND revoked_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_patient_active_consent ON patient_consents(patient_id, consent_type) WHERE status='GRANTED';
CREATE INDEX idx_patient_consent_history ON patient_consents(patient_id, consent_type, granted_at DESC);
CREATE TABLE patient_access_grants (
  patient_id UUID NOT NULL REFERENCES patients(id),
  doctor_id UUID NOT NULL,
  appointment_id UUID NOT NULL,
  granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  aggregate_version BIGINT NOT NULL DEFAULT 0,
  revoked_at TIMESTAMPTZ,
  PRIMARY KEY (patient_id, doctor_id, appointment_id),
  CHECK (expires_at > granted_at)
);
CREATE INDEX idx_patient_access_grants_scope ON patient_access_grants(patient_id, doctor_id, expires_at);
CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  aggregate_id UUID NOT NULL,
  aggregate_version BIGINT NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

