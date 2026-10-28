CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE clinic_settings (
  id SMALLINT PRIMARY KEY CHECK (id=1),
  timezone TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
  currency CHAR(3) NOT NULL DEFAULT 'VND',
  no_show_grace_minutes SMALLINT NOT NULL DEFAULT 15 CHECK (no_show_grace_minutes >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE doctors (
  user_id UUID PRIMARY KEY,
  display_name TEXT NOT NULL,
  license_number TEXT NOT NULL UNIQUE,
  specialty TEXT,
  is_bookable BOOLEAN NOT NULL DEFAULT true
);
CREATE TABLE chairs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL UNIQUE,
  room_type TEXT NOT NULL DEFAULT 'EXAMINATION' CHECK (room_type IN ('EXAMINATION','XRAY','SURGERY','CONSULTATION')),
  is_active BOOLEAN NOT NULL DEFAULT true
);
CREATE TABLE services (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  description TEXT,
  price NUMERIC(15,2) NOT NULL CHECK (price >= 0),
  duration_minutes SMALLINT NOT NULL CHECK (duration_minutes > 0 AND duration_minutes % 15 = 0),
  is_active BOOLEAN NOT NULL DEFAULT true,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE doctor_working_hours (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id UUID NOT NULL REFERENCES doctors(user_id),
  day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
  starts_at TIME NOT NULL,
  ends_at TIME NOT NULL,
  effective_from DATE NOT NULL DEFAULT DATE '2000-01-01',
  effective_until DATE,
  CHECK (ends_at > starts_at),
  CHECK (effective_until IS NULL OR effective_until >= effective_from)
);
CREATE INDEX idx_doctor_hours_lookup ON doctor_working_hours(doctor_id,day_of_week,effective_from,effective_until);
CREATE TABLE doctor_leave (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id UUID NOT NULL REFERENCES doctors(user_id),
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
  reason TEXT,
  CHECK (ends_at > starts_at)
);
CREATE INDEX idx_doctor_leave_range ON doctor_leave USING gist (doctor_id, tstzrange(starts_at,ends_at,'[)'));
CREATE TABLE clinic_closures (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  reason TEXT,
  CHECK (ends_at > starts_at),
  EXCLUDE USING gist (tstzrange(starts_at,ends_at,'[)') WITH &&)
);
CREATE TABLE appointments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  appointment_number TEXT NOT NULL UNIQUE,
  patient_id UUID NOT NULL,
  doctor_id UUID NOT NULL REFERENCES doctors(user_id),
  chair_id UUID NOT NULL REFERENCES chairs(id),
  scheduled_at TIMESTAMPTZ NOT NULL,
  duration_minutes SMALLINT NOT NULL CHECK (duration_minutes > 0 AND duration_minutes % 15 = 0),
  during TSTZRANGE NOT NULL,
  status TEXT NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW')),
  source TEXT NOT NULL DEFAULT 'STAFF' CHECK (source IN ('STAFF','PATIENT')),
  reason TEXT,
  patient_note TEXT,
  cancellation_reason TEXT,
  cancelled_at TIMESTAMPTZ,
  checked_in_at TIMESTAMPTZ,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  EXCLUDE USING gist (doctor_id WITH =, during WITH &&) WHERE (status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS')),
  EXCLUDE USING gist (patient_id WITH =, during WITH &&) WHERE (status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS')),
  EXCLUDE USING gist (chair_id WITH =, during WITH &&) WHERE (status IN ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS'))
);
CREATE FUNCTION set_appointment_range() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.during := tstzrange(NEW.scheduled_at,NEW.scheduled_at + NEW.duration_minutes * INTERVAL '1 minute','[)');
  RETURN NEW;
END $$;
CREATE TRIGGER trg_appointment_range BEFORE INSERT OR UPDATE OF scheduled_at,duration_minutes ON appointments
FOR EACH ROW EXECUTE FUNCTION set_appointment_range();
CREATE INDEX idx_appointments_day_doctor ON appointments(doctor_id,scheduled_at);
CREATE INDEX idx_appointments_patient_history ON appointments(patient_id,scheduled_at DESC);
CREATE TABLE appointment_services (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  appointment_id UUID NOT NULL REFERENCES appointments(id),
  service_id UUID NOT NULL,
  title_snapshot TEXT NOT NULL,
  estimated_unit_price NUMERIC(15,2) NOT NULL CHECK (estimated_unit_price >= 0),
  quantity NUMERIC(8,2) NOT NULL DEFAULT 1 CHECK (quantity > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE idempotency_records (
  actor_id UUID NOT NULL,
  idempotency_key TEXT NOT NULL,
  request_hash BYTEA NOT NULL CHECK (octet_length(request_hash)=32),
  appointment_id UUID REFERENCES appointments(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(actor_id,idempotency_key)
);
CREATE TABLE audit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_user_id UUID,
  actor_label TEXT NOT NULL,
  action TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id UUID NOT NULL,
  request_id TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE TABLE outbox_events (
  event_id UUID PRIMARY KEY,
  event_type TEXT NOT NULL,
  schema_version SMALLINT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  aggregate_id UUID NOT NULL,
  aggregate_version BIGINT NOT NULL,
  correlation_id TEXT NOT NULL,
  payload JSONB NOT NULL,
  attempts SMALLINT NOT NULL DEFAULT 0,
  published_at TIMESTAMPTZ,
  dead_lettered_at TIMESTAMPTZ,
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_pending ON outbox_events(created_at) WHERE published_at IS NULL;
CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  aggregate_id UUID NOT NULL,
  aggregate_version BIGINT NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

