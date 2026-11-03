-- This guard serializes booking against patient deletion/archive across service databases.
-- Rows are tombstones and intentionally have no FK to patient_db.
CREATE TABLE patient_lifecycle (
    patient_id UUID PRIMARY KEY,
    state TEXT NOT NULL CHECK (state IN ('ACTIVE','ARCHIVED','DELETED')),
    operation_id UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
