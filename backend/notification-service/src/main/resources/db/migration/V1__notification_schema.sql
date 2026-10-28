CREATE TABLE notifications (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id UUID,
  user_id UUID,
  channel TEXT NOT NULL CHECK(channel IN ('EMAIL','SMS','ZALO')),
  template_code TEXT NOT NULL,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('QUEUED','SENT','FAILED','SKIPPED')),
  scheduled_at TIMESTAMPTZ,
  sent_at TIMESTAMPTZ,
  idempotency_key UUID NOT NULL UNIQUE,
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_patient ON notifications(patient_id,created_at DESC);
CREATE TABLE notification_jobs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  notification_id UUID NOT NULL UNIQUE REFERENCES notifications(id),
  event_id UUID NOT NULL UNIQUE,
  recipient TEXT NOT NULL,
  subject TEXT NOT NULL,
  body TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','SENT','DEAD')),
  attempts SMALLINT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at TIMESTAMPTZ
);
CREATE INDEX idx_notification_jobs_pending ON notification_jobs(next_attempt_at) WHERE status='PENDING';
CREATE TABLE notification_attempts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id UUID NOT NULL REFERENCES notification_jobs(id),
  attempt_number SMALLINT NOT NULL CHECK(attempt_number>0),
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  outcome TEXT NOT NULL CHECK(outcome IN ('SENT','FAILED','DEAD')),
  error_message TEXT,
  UNIQUE(job_id,attempt_number)
);
CREATE TABLE processed_events (
  event_id UUID PRIMARY KEY,
  aggregate_id UUID NOT NULL,
  aggregate_version BIGINT NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

