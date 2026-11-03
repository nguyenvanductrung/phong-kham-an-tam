-- File SQL export của scheduling_db (dịch vụ scheduling-service), Phòng khám An Tâm.
-- Tạo từ các migration Flyway trong backend/scheduling-service/src/main/resources/db/migration (commit 39db848, 04/11/2026).
-- Gồm cấu trúc bảng, ràng buộc, chỉ mục, trigger, hàm. Dữ liệu demo do ứng dụng tự tạo khi khởi động nên không có trong file.
-- Cách nạp: CREATE DATABASE scheduling_db; rồi chạy  psql -d scheduling_db -f scheduling_db.sql

--
-- PostgreSQL database dump
--

\restrict gwba4ehLgsUuDeurLifrGPLtZi21rkeIXuQeVkJfSTH6rxxEK0vfrsAa8EZngbH

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
-- Name: btree_gist; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;


--
-- Name: EXTENSION btree_gist; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION btree_gist IS 'support for indexing common datatypes in GiST';


--
-- Name: set_appointment_range(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_appointment_range() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.during := tstzrange(NEW.scheduled_at,NEW.scheduled_at + NEW.duration_minutes * INTERVAL '1 minute','[)');
  RETURN NEW;
END $$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: appointment_services; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appointment_services (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    appointment_id uuid NOT NULL,
    service_id uuid NOT NULL,
    title_snapshot text NOT NULL,
    estimated_unit_price numeric(15,2) NOT NULL,
    quantity numeric(8,2) DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT appointment_services_estimated_unit_price_check CHECK ((estimated_unit_price >= (0)::numeric)),
    CONSTRAINT appointment_services_quantity_check CHECK ((quantity > (0)::numeric))
);


--
-- Name: appointments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appointments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    appointment_number text NOT NULL,
    patient_id uuid NOT NULL,
    doctor_id uuid NOT NULL,
    chair_id uuid NOT NULL,
    scheduled_at timestamp with time zone NOT NULL,
    duration_minutes smallint NOT NULL,
    during tstzrange NOT NULL,
    status text DEFAULT 'BOOKED'::text NOT NULL,
    source text DEFAULT 'STAFF'::text NOT NULL,
    reason text,
    patient_note text,
    cancellation_reason text,
    cancelled_at timestamp with time zone,
    checked_in_at timestamp with time zone,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT appointments_duration_minutes_check CHECK (((duration_minutes > 0) AND (((duration_minutes)::integer % 15) = 0))),
    CONSTRAINT appointments_source_check CHECK ((source = ANY (ARRAY['STAFF'::text, 'PATIENT'::text]))),
    CONSTRAINT appointments_status_check CHECK ((status = ANY (ARRAY['BOOKED'::text, 'CONFIRMED'::text, 'ARRIVED'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text, 'CANCELLED'::text, 'NO_SHOW'::text])))
);


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
-- Name: chairs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chairs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    room_type text DEFAULT 'EXAMINATION'::text NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    CONSTRAINT chairs_room_type_check CHECK ((room_type = ANY (ARRAY['EXAMINATION'::text, 'XRAY'::text, 'SURGERY'::text, 'CONSULTATION'::text])))
);


--
-- Name: clinic_closures; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinic_closures (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    starts_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone NOT NULL,
    reason text,
    CONSTRAINT clinic_closures_check CHECK ((ends_at > starts_at))
);


--
-- Name: clinic_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clinic_settings (
    id smallint NOT NULL,
    timezone text DEFAULT 'Asia/Ho_Chi_Minh'::text NOT NULL,
    currency character(3) DEFAULT 'VND'::bpchar NOT NULL,
    no_show_grace_minutes smallint DEFAULT 15 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT clinic_settings_id_check CHECK ((id = 1)),
    CONSTRAINT clinic_settings_no_show_grace_minutes_check CHECK ((no_show_grace_minutes >= 0))
);


--
-- Name: doctor_leave; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.doctor_leave (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    doctor_id uuid NOT NULL,
    starts_at timestamp with time zone NOT NULL,
    ends_at timestamp with time zone NOT NULL,
    status text NOT NULL,
    reason text,
    CONSTRAINT doctor_leave_check CHECK ((ends_at > starts_at)),
    CONSTRAINT doctor_leave_status_check CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text, 'CANCELLED'::text])))
);


--
-- Name: doctor_working_hours; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.doctor_working_hours (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    doctor_id uuid NOT NULL,
    day_of_week smallint NOT NULL,
    starts_at time without time zone NOT NULL,
    ends_at time without time zone NOT NULL,
    effective_from date DEFAULT '2000-01-01'::date NOT NULL,
    effective_until date,
    CONSTRAINT doctor_working_hours_check CHECK ((ends_at > starts_at)),
    CONSTRAINT doctor_working_hours_check1 CHECK (((effective_until IS NULL) OR (effective_until >= effective_from))),
    CONSTRAINT doctor_working_hours_day_of_week_check CHECK (((day_of_week >= 1) AND (day_of_week <= 7)))
);


--
-- Name: doctors; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.doctors (
    user_id uuid NOT NULL,
    display_name text NOT NULL,
    license_number text NOT NULL,
    specialty text,
    is_bookable boolean DEFAULT true NOT NULL
);


--
-- Name: idempotency_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.idempotency_records (
    actor_id uuid NOT NULL,
    idempotency_key text NOT NULL,
    request_hash bytea NOT NULL,
    appointment_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT idempotency_records_request_hash_check CHECK ((octet_length(request_hash) = 32))
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
-- Name: patient_lifecycle; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_lifecycle (
    patient_id uuid NOT NULL,
    state text NOT NULL,
    operation_id uuid NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT patient_lifecycle_state_check CHECK ((state = ANY (ARRAY['ACTIVE'::text, 'ARCHIVED'::text, 'DELETED'::text])))
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
-- Name: services; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.services (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code text NOT NULL,
    name text NOT NULL,
    description text,
    price numeric(15,2) NOT NULL,
    duration_minutes smallint NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT services_duration_minutes_check CHECK (((duration_minutes > 0) AND (((duration_minutes)::integer % 15) = 0))),
    CONSTRAINT services_price_check CHECK ((price >= (0)::numeric))
);


--
-- Data for Name: appointment_services; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.appointment_services (id, appointment_id, service_id, title_snapshot, estimated_unit_price, quantity, created_at) FROM stdin;
\.


--
-- Data for Name: appointments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.appointments (id, appointment_number, patient_id, doctor_id, chair_id, scheduled_at, duration_minutes, during, status, source, reason, patient_note, cancellation_reason, cancelled_at, checked_in_at, started_at, completed_at, version, created_by, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: audit_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.audit_events (id, occurred_at, actor_user_id, actor_label, action, entity_type, entity_id, request_id, metadata) FROM stdin;
\.


--
-- Data for Name: chairs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.chairs (id, name, room_type, is_active) FROM stdin;
\.


--
-- Data for Name: clinic_closures; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.clinic_closures (id, starts_at, ends_at, reason) FROM stdin;
\.


--
-- Data for Name: clinic_settings; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.clinic_settings (id, timezone, currency, no_show_grace_minutes, updated_at) FROM stdin;
\.


--
-- Data for Name: doctor_leave; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.doctor_leave (id, doctor_id, starts_at, ends_at, status, reason) FROM stdin;
\.


--
-- Data for Name: doctor_working_hours; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.doctor_working_hours (id, doctor_id, day_of_week, starts_at, ends_at, effective_from, effective_until) FROM stdin;
\.


--
-- Data for Name: doctors; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.doctors (user_id, display_name, license_number, specialty, is_bookable) FROM stdin;
\.


--
-- Data for Name: idempotency_records; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.idempotency_records (actor_id, idempotency_key, request_hash, appointment_id, created_at) FROM stdin;
\.


--
-- Data for Name: outbox_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.outbox_events (event_id, event_type, schema_version, occurred_at, aggregate_id, aggregate_version, correlation_id, payload, attempts, published_at, dead_lettered_at, last_error, created_at) FROM stdin;
\.


--
-- Data for Name: patient_lifecycle; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.patient_lifecycle (patient_id, state, operation_id, updated_at) FROM stdin;
\.


--
-- Data for Name: processed_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.processed_events (event_id, aggregate_id, aggregate_version, processed_at) FROM stdin;
\.


--
-- Data for Name: services; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.services (id, code, name, description, price, duration_minutes, is_active, updated_at) FROM stdin;
\.


--
-- Name: appointment_services appointment_services_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment_services
    ADD CONSTRAINT appointment_services_pkey PRIMARY KEY (id);


--
-- Name: appointments appointments_appointment_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_appointment_number_key UNIQUE (appointment_number);


--
-- Name: appointments appointments_chair_id_during_excl; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_chair_id_during_excl EXCLUDE USING gist (chair_id WITH =, during WITH &&) WHERE ((status = ANY (ARRAY['BOOKED'::text, 'CONFIRMED'::text, 'ARRIVED'::text, 'IN_PROGRESS'::text])));


--
-- Name: appointments appointments_doctor_id_during_excl; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_doctor_id_during_excl EXCLUDE USING gist (doctor_id WITH =, during WITH &&) WHERE ((status = ANY (ARRAY['BOOKED'::text, 'CONFIRMED'::text, 'ARRIVED'::text, 'IN_PROGRESS'::text])));


--
-- Name: appointments appointments_patient_id_during_excl; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_patient_id_during_excl EXCLUDE USING gist (patient_id WITH =, during WITH &&) WHERE ((status = ANY (ARRAY['BOOKED'::text, 'CONFIRMED'::text, 'ARRIVED'::text, 'IN_PROGRESS'::text])));


--
-- Name: appointments appointments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_pkey PRIMARY KEY (id);


--
-- Name: audit_events audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_events
    ADD CONSTRAINT audit_events_pkey PRIMARY KEY (id);


--
-- Name: chairs chairs_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chairs
    ADD CONSTRAINT chairs_name_key UNIQUE (name);


--
-- Name: chairs chairs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chairs
    ADD CONSTRAINT chairs_pkey PRIMARY KEY (id);


--
-- Name: clinic_closures clinic_closures_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_closures
    ADD CONSTRAINT clinic_closures_pkey PRIMARY KEY (id);


--
-- Name: clinic_closures clinic_closures_tstzrange_excl; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_closures
    ADD CONSTRAINT clinic_closures_tstzrange_excl EXCLUDE USING gist (tstzrange(starts_at, ends_at, '[)'::text) WITH &&);


--
-- Name: clinic_settings clinic_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clinic_settings
    ADD CONSTRAINT clinic_settings_pkey PRIMARY KEY (id);


--
-- Name: doctor_leave doctor_leave_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_leave
    ADD CONSTRAINT doctor_leave_pkey PRIMARY KEY (id);


--
-- Name: doctor_working_hours doctor_working_hours_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_working_hours
    ADD CONSTRAINT doctor_working_hours_pkey PRIMARY KEY (id);


--
-- Name: doctors doctors_license_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctors
    ADD CONSTRAINT doctors_license_number_key UNIQUE (license_number);


--
-- Name: doctors doctors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctors
    ADD CONSTRAINT doctors_pkey PRIMARY KEY (user_id);


--
-- Name: idempotency_records idempotency_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_records
    ADD CONSTRAINT idempotency_records_pkey PRIMARY KEY (actor_id, idempotency_key);


--
-- Name: outbox_events outbox_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (event_id);


--
-- Name: patient_lifecycle patient_lifecycle_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_lifecycle
    ADD CONSTRAINT patient_lifecycle_pkey PRIMARY KEY (patient_id);


--
-- Name: processed_events processed_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.processed_events
    ADD CONSTRAINT processed_events_pkey PRIMARY KEY (event_id);


--
-- Name: services services_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.services
    ADD CONSTRAINT services_code_key UNIQUE (code);


--
-- Name: services services_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.services
    ADD CONSTRAINT services_pkey PRIMARY KEY (id);


--
-- Name: idx_appointments_day_doctor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointments_day_doctor ON public.appointments USING btree (doctor_id, scheduled_at);


--
-- Name: idx_appointments_patient_history; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointments_patient_history ON public.appointments USING btree (patient_id, scheduled_at DESC);


--
-- Name: idx_doctor_hours_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doctor_hours_lookup ON public.doctor_working_hours USING btree (doctor_id, day_of_week, effective_from, effective_until);


--
-- Name: idx_doctor_leave_range; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doctor_leave_range ON public.doctor_leave USING gist (doctor_id, tstzrange(starts_at, ends_at, '[)'::text));


--
-- Name: idx_outbox_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbox_pending ON public.outbox_events USING btree (created_at) WHERE (published_at IS NULL);


--
-- Name: appointments trg_appointment_range; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_appointment_range BEFORE INSERT OR UPDATE OF scheduled_at, duration_minutes ON public.appointments FOR EACH ROW EXECUTE FUNCTION public.set_appointment_range();


--
-- Name: appointment_services appointment_services_appointment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointment_services
    ADD CONSTRAINT appointment_services_appointment_id_fkey FOREIGN KEY (appointment_id) REFERENCES public.appointments(id);


--
-- Name: appointments appointments_chair_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_chair_id_fkey FOREIGN KEY (chair_id) REFERENCES public.chairs(id);


--
-- Name: appointments appointments_doctor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_doctor_id_fkey FOREIGN KEY (doctor_id) REFERENCES public.doctors(user_id);


--
-- Name: doctor_leave doctor_leave_doctor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_leave
    ADD CONSTRAINT doctor_leave_doctor_id_fkey FOREIGN KEY (doctor_id) REFERENCES public.doctors(user_id);


--
-- Name: doctor_working_hours doctor_working_hours_doctor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.doctor_working_hours
    ADD CONSTRAINT doctor_working_hours_doctor_id_fkey FOREIGN KEY (doctor_id) REFERENCES public.doctors(user_id);


--
-- Name: idempotency_records idempotency_records_appointment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_records
    ADD CONSTRAINT idempotency_records_appointment_id_fkey FOREIGN KEY (appointment_id) REFERENCES public.appointments(id);


--
-- PostgreSQL database dump complete
--

\unrestrict gwba4ehLgsUuDeurLifrGPLtZi21rkeIXuQeVkJfSTH6rxxEK0vfrsAa8EZngbH

