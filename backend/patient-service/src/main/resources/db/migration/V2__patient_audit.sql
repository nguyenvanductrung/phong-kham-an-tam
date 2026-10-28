CREATE TABLE patient_audit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_user_id UUID,
  actor_label TEXT NOT NULL,
  action TEXT NOT NULL,
  patient_id UUID NOT NULL,
  request_id TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_patient_audit_patient ON patient_audit_events(patient_id,occurred_at DESC);
