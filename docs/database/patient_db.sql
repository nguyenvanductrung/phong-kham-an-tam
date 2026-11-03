-- File SQL export của patient_db (dịch vụ patient-service), Phòng khám An Tâm.
-- Tạo từ các migration Flyway trong backend/patient-service/src/main/resources/db/migration (commit 39db848, 04/11/2026).
-- Gồm cấu trúc bảng, ràng buộc, chỉ mục, trigger, hàm. Dữ liệu demo do ứng dụng tự tạo khi khởi động nên không có trong file.
-- Cách nạp: CREATE DATABASE patient_db; rồi chạy  psql -d patient_db -f patient_db.sql

--
-- PostgreSQL database dump
--

\restrict ZxCv58hqs2mEoHCuZnyFbKw20rEIX4e8OuNdhYIlgwXbauj4HHHD3eeSVIFUcC1

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

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: patient_access_grants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_access_grants (
    patient_id uuid NOT NULL,
    doctor_id uuid NOT NULL,
    appointment_id uuid NOT NULL,
    granted_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    aggregate_version bigint DEFAULT 0 NOT NULL,
    revoked_at timestamp with time zone,
    CONSTRAINT patient_access_grants_check CHECK ((expires_at > granted_at))
);


--
-- Name: patient_account_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_account_links (
    patient_id uuid NOT NULL,
    user_id uuid NOT NULL,
    linked_at timestamp with time zone DEFAULT now() NOT NULL,
    linked_by uuid
);


--
-- Name: patient_audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_audit_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    actor_user_id uuid,
    actor_label text NOT NULL,
    action text NOT NULL,
    patient_id uuid NOT NULL,
    request_id text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL
);


--
-- Name: patient_consents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patient_consents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    patient_id uuid NOT NULL,
    consent_type text NOT NULL,
    status text NOT NULL,
    granted_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    channel text,
    policy_version text,
    evidence_key text,
    recorded_by uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT patient_consents_check CHECK ((((status = 'GRANTED'::text) AND (revoked_at IS NULL)) OR ((status = 'REVOKED'::text) AND (revoked_at IS NOT NULL)))),
    CONSTRAINT patient_consents_consent_type_check CHECK ((consent_type = ANY (ARRAY['DATA_PROCESSING'::text, 'MARKETING_ZALO'::text, 'MARKETING_SMS'::text, 'MARKETING_EMAIL'::text, 'PHOTO_USE'::text, 'GENERAL_TREATMENT'::text]))),
    CONSTRAINT patient_consents_status_check CHECK ((status = ANY (ARRAY['GRANTED'::text, 'REVOKED'::text])))
);


--
-- Name: patients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.patients (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    patient_code text NOT NULL,
    full_name text NOT NULL,
    phone text NOT NULL,
    email text,
    gender text,
    date_of_birth date,
    address text,
    occupation text,
    emergency_contact text,
    emergency_phone text,
    blood_type text,
    rh_factor text,
    allergies text,
    medical_history text,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    created_by uuid,
    CONSTRAINT patients_blood_type_check CHECK (((blood_type IS NULL) OR (blood_type = ANY (ARRAY['A'::text, 'B'::text, 'AB'::text, 'O'::text])))),
    CONSTRAINT patients_gender_check CHECK (((gender IS NULL) OR (gender = ANY (ARRAY['MALE'::text, 'FEMALE'::text, 'OTHER'::text])))),
    CONSTRAINT patients_rh_factor_check CHECK (((rh_factor IS NULL) OR (rh_factor = ANY (ARRAY['POSITIVE'::text, 'NEGATIVE'::text]))))
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
-- Data for Name: patient_access_grants; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.patient_access_grants (patient_id, doctor_id, appointment_id, granted_at, expires_at, aggregate_version, revoked_at) FROM stdin;
\.


--
-- Data for Name: patient_account_links; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.patient_account_links (patient_id, user_id, linked_at, linked_by) FROM stdin;
\.


--
-- Data for Name: patient_audit_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.patient_audit_events (id, occurred_at, actor_user_id, actor_label, action, patient_id, request_id, metadata) FROM stdin;
\.


--
-- Data for Name: patient_consents; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.patient_consents (id, patient_id, consent_type, status, granted_at, revoked_at, channel, policy_version, evidence_key, recorded_by, created_at) FROM stdin;
\.


--
-- Data for Name: patients; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.patients (id, patient_code, full_name, phone, email, gender, date_of_birth, address, occupation, emergency_contact, emergency_phone, blood_type, rh_factor, allergies, medical_history, notes, created_at, updated_at, deleted_at, created_by) FROM stdin;
\.


--
-- Data for Name: processed_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.processed_events (event_id, aggregate_id, aggregate_version, processed_at) FROM stdin;
\.


--
-- Name: patient_access_grants patient_access_grants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_access_grants
    ADD CONSTRAINT patient_access_grants_pkey PRIMARY KEY (patient_id, doctor_id, appointment_id);


--
-- Name: patient_account_links patient_account_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_account_links
    ADD CONSTRAINT patient_account_links_pkey PRIMARY KEY (patient_id);


--
-- Name: patient_account_links patient_account_links_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_account_links
    ADD CONSTRAINT patient_account_links_user_id_key UNIQUE (user_id);


--
-- Name: patient_audit_events patient_audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_audit_events
    ADD CONSTRAINT patient_audit_events_pkey PRIMARY KEY (id);


--
-- Name: patient_consents patient_consents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_consents
    ADD CONSTRAINT patient_consents_pkey PRIMARY KEY (id);


--
-- Name: patients patients_patient_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patients
    ADD CONSTRAINT patients_patient_code_key UNIQUE (patient_code);


--
-- Name: patients patients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patients
    ADD CONSTRAINT patients_pkey PRIMARY KEY (id);


--
-- Name: processed_events processed_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.processed_events
    ADD CONSTRAINT processed_events_pkey PRIMARY KEY (event_id);


--
-- Name: idx_patient_access_grants_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_patient_access_grants_scope ON public.patient_access_grants USING btree (patient_id, doctor_id, expires_at);


--
-- Name: idx_patient_audit_patient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_patient_audit_patient ON public.patient_audit_events USING btree (patient_id, occurred_at DESC);


--
-- Name: idx_patient_consent_history; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_patient_consent_history ON public.patient_consents USING btree (patient_id, consent_type, granted_at DESC);


--
-- Name: idx_patients_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_patients_name ON public.patients USING btree (lower(full_name));


--
-- Name: idx_patients_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_patients_phone ON public.patients USING btree (phone);


--
-- Name: uq_patient_active_consent; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_patient_active_consent ON public.patient_consents USING btree (patient_id, consent_type) WHERE (status = 'GRANTED'::text);


--
-- Name: patient_access_grants patient_access_grants_patient_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_access_grants
    ADD CONSTRAINT patient_access_grants_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES public.patients(id);


--
-- Name: patient_account_links patient_account_links_patient_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_account_links
    ADD CONSTRAINT patient_account_links_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES public.patients(id);


--
-- Name: patient_consents patient_consents_patient_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.patient_consents
    ADD CONSTRAINT patient_consents_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES public.patients(id);


--
-- PostgreSQL database dump complete
--

\unrestrict ZxCv58hqs2mEoHCuZnyFbKw20rEIX4e8OuNdhYIlgwXbauj4HHHD3eeSVIFUcC1

