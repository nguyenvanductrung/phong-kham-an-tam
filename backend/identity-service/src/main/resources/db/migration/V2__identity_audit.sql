CREATE TABLE identity_audit_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_user_id UUID,
  actor_label TEXT NOT NULL,
  action TEXT NOT NULL,
  target_user_id UUID NOT NULL,
  request_id TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_identity_audit_target ON identity_audit_events(target_user_id,occurred_at DESC);
