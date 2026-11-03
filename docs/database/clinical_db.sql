-- File SQL export của clinical_db (dịch vụ clinical-service), Phòng khám An Tâm.
-- Tạo từ các migration Flyway trong backend/clinical-service/src/main/resources/db/migration (commit 39db848, 04/11/2026).
-- Gồm cấu trúc bảng, ràng buộc, chỉ mục, trigger, hàm. Dữ liệu demo do ứng dụng tự tạo khi khởi động nên không có trong file.
-- Cách nạp: CREATE DATABASE clinical_db; rồi chạy  psql -d clinical_db -f clinical_db.sql

--
-- PostgreSQL database dump
--

\restrict 810VZILSLn0akamYYlHvqoZCl7hLad2bC69f84ghvHusJlVMYYQEfGcfC29S6xD

-- Dumped from database version 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)
-- Dumped by pg_dump version 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: prevent_approved_plan_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_approved_plan_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF OLD.status='APPROVED' THEN RAISE EXCEPTION 'Approved treatment plans are immutable' USING ERRCODE='55000'; END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;


--
-- Name: prevent_issued_prescription_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_issued_prescription_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF OLD.status='ISSUED' THEN RAISE EXCEPTION 'Issued prescriptions are immutable' USING ERRCODE='55000'; END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;


--
-- Name: prevent_signed_record_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_signed_record_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF OLD.status='SIGNED' THEN RAISE EXCEPTION 'Signed clinical records are immutable' USING ERRCODE='55000'; END IF;
  IF TG_OP='DELETE' THEN RETURN OLD; END IF;
  RETURN NEW;
END $$;


--
-- Name: require_draft_clinical_parent(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.require_draft_clinical_parent() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: require_draft_plan_parent(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.require_draft_plan_parent() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: require_draft_prescription_parent(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.require_draft_prescription_parent() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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


--
-- Name: require_signed_record_for_prescription(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.require_signed_record_for_prescription() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE parent_status TEXT;
BEGIN
  SELECT status INTO parent_status FROM clinical_records WHERE id=NEW.record_id AND doctor_id=NEW.doctor_id AND patient_id=NEW.patient_id;
  IF parent_status IS DISTINCT FROM 'SIGNED' THEN RAISE EXCEPTION 'Prescription requires a signed record' USING ERRCODE='23514'; END IF;
  RETURN NEW;
END $$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    actor_user_id uuid,
    actor_label text NOT NULL,
    action text NOT NULL,
    entity_type text NOT NULL,
    entity_id uuid NOT NULL,
    request_id text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL
);


--
-- Name: clinical_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinical_records (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    record_number text NOT NULL,
    patient_id uuid NOT NULL,
    appointment_id uuid NOT NULL,
    doctor_id uuid NOT NULL,
    visit_at timestamp with time zone NOT NULL,
    chief_complaint text,
    medical_history text,
    examination_notes text,
    diagnosis text,
    treatment_notes text,
    follow_up_at timestamp with time zone,
    status text DEFAULT 'DRAFT'::text NOT NULL,
    amends_record_id uuid,
    amendment_reason text,
    signed_at timestamp with time zone,
    signed_by uuid,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT clinical_records_check CHECK ((((status = 'DRAFT'::text) AND (signed_at IS NULL) AND (signed_by IS NULL)) OR ((status = 'SIGNED'::text) AND (signed_at IS NOT NULL) AND (signed_by = doctor_id)))),
    CONSTRAINT clinical_records_check1 CHECK ((((amends_record_id IS NULL) AND (amendment_reason IS NULL)) OR ((amends_record_id IS NOT NULL) AND (length(TRIM(BOTH FROM amendment_reason)) > 0)))),
    CONSTRAINT clinical_records_status_check CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SIGNED'::text])))
);


--
-- Name: dental_record_teeth; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dental_record_teeth (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    record_id uuid NOT NULL,
    tooth_number smallint NOT NULL,
    condition_code text NOT NULL,
    surfaces text[] DEFAULT '{}'::text[] NOT NULL,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT dental_record_teeth_tooth_number_check CHECK ((tooth_number = ANY (ARRAY[11, 12, 13, 14, 15, 16, 17, 18, 21, 22, 23, 24, 25, 26, 27, 28, 31, 32, 33, 34, 35, 36, 37, 38, 41, 42, 43, 44, 45, 46, 47, 48, 51, 52, 53, 54, 55, 61, 62, 63, 64, 65, 71, 72, 73, 74, 75, 81, 82, 83, 84, 85])))
);


--
-- Name: outbox_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outbox_events (
    event_id uuid NOT NULL,
    event_type text NOT NULL,
    schema_version smallint NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_version bigint NOT NULL,
    correlation_id text NOT NULL,
    payload jsonb NOT NULL,
    attempts smallint DEFAULT 0 NOT NULL,
    published_at timestamp with time zone,
    dead_lettered_at timestamp with time zone,
    last_error text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: patient_files; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_files (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    patient_id uuid NOT NULL,
    record_id uuid NOT NULL,
    file_kind text NOT NULL,
    object_key text NOT NULL,
    original_filename text NOT NULL,
    mime_type text NOT NULL,
    byte_size bigint NOT NULL,
    checksum_sha256 text NOT NULL,
    uploaded_by uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT patient_files_byte_size_check CHECK (((byte_size > 0) AND (byte_size <= 20971520)))
);


--
-- Name: performed_procedures; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.performed_procedures (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    patient_id uuid NOT NULL,
    record_id uuid NOT NULL,
    doctor_id uuid NOT NULL,
    service_id uuid NOT NULL,
    title_snapshot text NOT NULL,
    tooth_numbers integer[] DEFAULT '{}'::integer[] NOT NULL,
    quantity numeric(8,2) DEFAULT 1 NOT NULL,
    unit_price numeric(15,2) NOT NULL,
    discount_amount numeric(15,2) DEFAULT 0 NOT NULL,
    net_amount numeric(15,2) GENERATED ALWAYS AS (((quantity * unit_price) - discount_amount)) STORED,
    performed_at timestamp with time zone DEFAULT now() NOT NULL,
    notes text,
    created_by uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT performed_procedures_check CHECK ((discount_amount <= (quantity * unit_price))),
    CONSTRAINT performed_procedures_discount_amount_check CHECK ((discount_amount >= (0)::numeric)),
    CONSTRAINT performed_procedures_quantity_check CHECK ((quantity > (0)::numeric)),
    CONSTRAINT performed_procedures_unit_price_check CHECK ((unit_price >= (0)::numeric))
);


--
-- Name: prescription_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.prescription_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    prescription_id uuid NOT NULL,
    medication_name text NOT NULL,
    dosage text NOT NULL,
    route text NOT NULL,
    frequency text NOT NULL,
    duration_days smallint NOT NULL,
    quantity text NOT NULL,
    instructions text,
    CONSTRAINT prescription_items_duration_days_check CHECK ((duration_days > 0))
);


--
-- Name: prescriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.prescriptions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    prescription_number text NOT NULL,
    patient_id uuid NOT NULL,
    record_id uuid NOT NULL,
    doctor_id uuid NOT NULL,
    issued_at timestamp with time zone DEFAULT now() NOT NULL,
    diagnosis_snapshot text,
    notes text,
    status text DEFAULT 'DRAFT'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT prescriptions_status_check CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ISSUED'::text])))
);


--
-- Name: processed_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.processed_events (
    event_id uuid NOT NULL,
    aggregate_id uuid NOT NULL,
    aggregate_version bigint NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: treatment_plan_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.treatment_plan_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    plan_id uuid NOT NULL,
    sequence_no smallint NOT NULL,
    service_id uuid NOT NULL,
    title_snapshot text NOT NULL,
    quantity numeric(8,2) DEFAULT 1 NOT NULL,
    unit_price numeric(15,2) NOT NULL,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT treatment_plan_items_quantity_check CHECK ((quantity > (0)::numeric)),
    CONSTRAINT treatment_plan_items_sequence_no_check CHECK ((sequence_no > 0)),
    CONSTRAINT treatment_plan_items_unit_price_check CHECK ((unit_price >= (0)::numeric))
);


--
-- Name: treatment_plans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.treatment_plans (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    patient_id uuid NOT NULL,
    clinical_record_id uuid NOT NULL,
    doctor_id uuid NOT NULL,
    title text NOT NULL,
    status text DEFAULT 'DRAFT'::text NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    approved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT treatment_plans_check CHECK ((((status = 'DRAFT'::text) AND (approved_at IS NULL)) OR ((status = 'APPROVED'::text) AND (approved_at IS NOT NULL)))),
    CONSTRAINT treatment_plans_status_check CHECK ((status = ANY (ARRAY['DRAFT'::text, 'APPROVED'::text])))
);


--
-- Data for Name: audit_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.audit_events (id, occurred_at, actor_user_id, actor_label, action, entity_type, entity_id, request_id, metadata) FROM stdin;
\.


--
-- Data for Name: clinical_records; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.clinical_records (id, record_number, patient_id, appointment_id, doctor_id, visit_at, chief_complaint, medical_history, examination_notes, diagnosis, treatment_notes, follow_up_at, status, amends_record_id, amendment_reason, signed_at, signed_by, version, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: dental_record_teeth; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.dental_record_teeth (id, record_id, tooth_number, condition_code, surfaces, notes, created_at) FROM stdin;
\.


--
-- Data for Name: outbox_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.outbox_events (event_id, event_type, schema_version, occurred_at, aggregate_id, aggregate_version, correlation_id, payload, attempts, published_at, dead_lettered_at, last_error, created_at) FROM stdin;
\.


--
-- Data for Name: patient_files; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.patient_files (id, patient_id, record_id, file_kind, object_key, original_filename, mime_type, byte_size, checksum_sha256, uploaded_by, created_at) FROM stdin;
\.


--
-- Data for Name: performed_procedures; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.performed_procedures (id, patient_id, record_id, doctor_id, service_id, title_snapshot, tooth_numbers, quantity, unit_price, discount_amount, performed_at, notes, created_by, created_at) FROM stdin;
\.


--
-- Data for Name: prescription_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.prescription_items (id, prescription_id, medication_name, dosage, route, frequency, duration_days, quantity, instructions) FROM stdin;
\.


--
-- Data for Name: prescriptions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.prescriptions (id, prescription_number, patient_id, record_id, doctor_id, issued_at, diagnosis_snapshot, notes, status, created_at) FROM stdin;
\.


--
-- Data for Name: processed_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.processed_events (event_id, aggregate_id, aggregate_version, processed_at) FROM stdin;
\.


--
-- Data for Name: treatment_plan_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.treatment_plan_items (id, plan_id, sequence_no, service_id, title_snapshot, quantity, unit_price, notes, created_at) FROM stdin;
\.


--
-- Data for Name: treatment_plans; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.treatment_plans (id, patient_id, clinical_record_id, doctor_id, title, status, version, approved_at, created_at, updated_at) FROM stdin;
\.


--
-- Name: audit_events audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_events
    ADD CONSTRAINT audit_events_pkey PRIMARY KEY (id);


--
-- Name: clinical_records clinical_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_records
    ADD CONSTRAINT clinical_records_pkey PRIMARY KEY (id);


--
-- Name: clinical_records clinical_records_record_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_records
    ADD CONSTRAINT clinical_records_record_number_key UNIQUE (record_number);


--
-- Name: dental_record_teeth dental_record_teeth_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dental_record_teeth
    ADD CONSTRAINT dental_record_teeth_pkey PRIMARY KEY (id);


--
-- Name: dental_record_teeth dental_record_teeth_record_id_tooth_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dental_record_teeth
    ADD CONSTRAINT dental_record_teeth_record_id_tooth_number_key UNIQUE (record_id, tooth_number);


--
-- Name: outbox_events outbox_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (event_id);


--
-- Name: patient_files patient_files_object_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_files
    ADD CONSTRAINT patient_files_object_key_key UNIQUE (object_key);


--
-- Name: patient_files patient_files_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_files
    ADD CONSTRAINT patient_files_pkey PRIMARY KEY (id);


--
-- Name: performed_procedures performed_procedures_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.performed_procedures
    ADD CONSTRAINT performed_procedures_pkey PRIMARY KEY (id);


--
-- Name: prescription_items prescription_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescription_items
    ADD CONSTRAINT prescription_items_pkey PRIMARY KEY (id);


--
-- Name: prescriptions prescriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescriptions
    ADD CONSTRAINT prescriptions_pkey PRIMARY KEY (id);


--
-- Name: prescriptions prescriptions_prescription_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescriptions
    ADD CONSTRAINT prescriptions_prescription_number_key UNIQUE (prescription_number);


--
-- Name: processed_events processed_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.processed_events
    ADD CONSTRAINT processed_events_pkey PRIMARY KEY (event_id);


--
-- Name: treatment_plan_items treatment_plan_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.treatment_plan_items
    ADD CONSTRAINT treatment_plan_items_pkey PRIMARY KEY (id);


--
-- Name: treatment_plan_items treatment_plan_items_plan_id_sequence_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.treatment_plan_items
    ADD CONSTRAINT treatment_plan_items_plan_id_sequence_no_key UNIQUE (plan_id, sequence_no);


--
-- Name: treatment_plans treatment_plans_clinical_record_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.treatment_plans
    ADD CONSTRAINT treatment_plans_clinical_record_id_key UNIQUE (clinical_record_id);


--
-- Name: treatment_plans treatment_plans_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.treatment_plans
    ADD CONSTRAINT treatment_plans_pkey PRIMARY KEY (id);


--
-- Name: idx_clinical_records_patient_history; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_clinical_records_patient_history ON public.clinical_records USING btree (patient_id, visit_at DESC);


--
-- Name: idx_outbox_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbox_pending ON public.outbox_events USING btree (created_at) WHERE (published_at IS NULL);


--
-- Name: uq_original_record_per_appointment; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_original_record_per_appointment ON public.clinical_records USING btree (appointment_id) WHERE (amends_record_id IS NULL);


--
-- Name: clinical_records trg_clinical_records_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_clinical_records_immutable BEFORE DELETE OR UPDATE ON public.clinical_records FOR EACH ROW EXECUTE FUNCTION public.prevent_signed_record_mutation();


--
-- Name: performed_procedures trg_performed_procedures_draft; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_performed_procedures_draft BEFORE INSERT OR DELETE OR UPDATE ON public.performed_procedures FOR EACH ROW EXECUTE FUNCTION public.require_draft_clinical_parent();


--
-- Name: prescriptions trg_prescription_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_prescription_immutable BEFORE DELETE OR UPDATE ON public.prescriptions FOR EACH ROW EXECUTE FUNCTION public.prevent_issued_prescription_mutation();


--
-- Name: prescription_items trg_prescription_items_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_prescription_items_immutable BEFORE INSERT OR DELETE OR UPDATE ON public.prescription_items FOR EACH ROW EXECUTE FUNCTION public.require_draft_prescription_parent();


--
-- Name: prescriptions trg_prescription_signed_record; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_prescription_signed_record BEFORE INSERT ON public.prescriptions FOR EACH ROW EXECUTE FUNCTION public.require_signed_record_for_prescription();


--
-- Name: dental_record_teeth trg_record_teeth_draft; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_record_teeth_draft BEFORE INSERT OR DELETE OR UPDATE ON public.dental_record_teeth FOR EACH ROW EXECUTE FUNCTION public.require_draft_clinical_parent();


--
-- Name: treatment_plans trg_treatment_plan_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_treatment_plan_immutable BEFORE DELETE OR UPDATE ON public.treatment_plans FOR EACH ROW EXECUTE FUNCTION public.prevent_approved_plan_mutation();


--
-- Name: treatment_plan_items trg_treatment_plan_items_draft; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_treatment_plan_items_draft BEFORE INSERT OR DELETE OR UPDATE ON public.treatment_plan_items FOR EACH ROW EXECUTE FUNCTION public.require_draft_plan_parent();


--
-- Name: clinical_records clinical_records_amends_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinical_records
    ADD CONSTRAINT clinical_records_amends_record_id_fkey FOREIGN KEY (amends_record_id) REFERENCES public.clinical_records(id);


--
-- Name: dental_record_teeth dental_record_teeth_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dental_record_teeth
    ADD CONSTRAINT dental_record_teeth_record_id_fkey FOREIGN KEY (record_id) REFERENCES public.clinical_records(id);


--
-- Name: patient_files patient_files_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_files
    ADD CONSTRAINT patient_files_record_id_fkey FOREIGN KEY (record_id) REFERENCES public.clinical_records(id);


--
-- Name: performed_procedures performed_procedures_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.performed_procedures
    ADD CONSTRAINT performed_procedures_record_id_fkey FOREIGN KEY (record_id) REFERENCES public.clinical_records(id);


--
-- Name: prescription_items prescription_items_prescription_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescription_items
    ADD CONSTRAINT prescription_items_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES public.prescriptions(id);


--
-- Name: prescriptions prescriptions_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.prescriptions
    ADD CONSTRAINT prescriptions_record_id_fkey FOREIGN KEY (record_id) REFERENCES public.clinical_records(id);


--
-- Name: treatment_plan_items treatment_plan_items_plan_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.treatment_plan_items
    ADD CONSTRAINT treatment_plan_items_plan_id_fkey FOREIGN KEY (plan_id) REFERENCES public.treatment_plans(id);


--
-- Name: treatment_plans treatment_plans_clinical_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.treatment_plans
    ADD CONSTRAINT treatment_plans_clinical_record_id_fkey FOREIGN KEY (clinical_record_id) REFERENCES public.clinical_records(id);


--
-- PostgreSQL database dump complete
--

\unrestrict 810VZILSLn0akamYYlHvqoZCl7hLad2bC69f84ghvHusJlVMYYQEfGcfC29S6xD

