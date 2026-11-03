-- File SQL export của notification_db (dịch vụ notification-service), Phòng khám An Tâm.
-- Tạo từ các migration Flyway trong backend/notification-service/src/main/resources/db/migration (commit 39db848, 04/11/2026).
-- Gồm cấu trúc bảng, ràng buộc, chỉ mục, trigger, hàm. Dữ liệu demo do ứng dụng tự tạo khi khởi động nên không có trong file.
-- Cách nạp: CREATE DATABASE notification_db; rồi chạy  psql -d notification_db -f notification_db.sql

--
-- PostgreSQL database dump
--

\restrict y4tnd66uP9wthkkxECamYgavSjmFgIMvLEo9KC5ISOxWYgOjICL56FQyoeR0b5T

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
-- Name: notification_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_attempts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    job_id uuid NOT NULL,
    attempt_number smallint NOT NULL,
    attempted_at timestamp with time zone DEFAULT now() NOT NULL,
    outcome text NOT NULL,
    error_message text,
    CONSTRAINT notification_attempts_attempt_number_check CHECK ((attempt_number > 0)),
    CONSTRAINT notification_attempts_outcome_check CHECK ((outcome = ANY (ARRAY['SENT'::text, 'FAILED'::text, 'DEAD'::text])))
);


--
-- Name: notification_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_jobs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    notification_id uuid NOT NULL,
    event_id uuid NOT NULL,
    recipient text NOT NULL,
    subject text NOT NULL,
    body text NOT NULL,
    status text DEFAULT 'PENDING'::text NOT NULL,
    attempts smallint DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    last_error text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    sent_at timestamp with time zone,
    CONSTRAINT notification_jobs_status_check CHECK ((status = ANY (ARRAY['PENDING'::text, 'SENT'::text, 'DEAD'::text])))
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    patient_id uuid,
    user_id uuid,
    channel text NOT NULL,
    template_code text NOT NULL,
    title text NOT NULL,
    body text NOT NULL,
    status text NOT NULL,
    scheduled_at timestamp with time zone,
    sent_at timestamp with time zone,
    idempotency_key uuid NOT NULL,
    error_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT notifications_channel_check CHECK ((channel = ANY (ARRAY['EMAIL'::text, 'SMS'::text, 'ZALO'::text]))),
    CONSTRAINT notifications_status_check CHECK ((status = ANY (ARRAY['QUEUED'::text, 'SENT'::text, 'FAILED'::text, 'SKIPPED'::text])))
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
-- Data for Name: notification_attempts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notification_attempts (id, job_id, attempt_number, attempted_at, outcome, error_message) FROM stdin;
\.


--
-- Data for Name: notification_jobs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notification_jobs (id, notification_id, event_id, recipient, subject, body, status, attempts, next_attempt_at, last_error, created_at, sent_at) FROM stdin;
\.


--
-- Data for Name: notifications; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.notifications (id, patient_id, user_id, channel, template_code, title, body, status, scheduled_at, sent_at, idempotency_key, error_message, created_at) FROM stdin;
\.


--
-- Data for Name: processed_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.processed_events (event_id, aggregate_id, aggregate_version, processed_at) FROM stdin;
\.


--
-- Name: notification_attempts notification_attempts_job_id_attempt_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_attempts
    ADD CONSTRAINT notification_attempts_job_id_attempt_number_key UNIQUE (job_id, attempt_number);


--
-- Name: notification_attempts notification_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_attempts
    ADD CONSTRAINT notification_attempts_pkey PRIMARY KEY (id);


--
-- Name: notification_jobs notification_jobs_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_jobs
    ADD CONSTRAINT notification_jobs_event_id_key UNIQUE (event_id);


--
-- Name: notification_jobs notification_jobs_notification_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_jobs
    ADD CONSTRAINT notification_jobs_notification_id_key UNIQUE (notification_id);


--
-- Name: notification_jobs notification_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_jobs
    ADD CONSTRAINT notification_jobs_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: processed_events processed_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.processed_events
    ADD CONSTRAINT processed_events_pkey PRIMARY KEY (event_id);


--
-- Name: idx_notification_jobs_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notification_jobs_pending ON public.notification_jobs USING btree (next_attempt_at) WHERE (status = 'PENDING'::text);


--
-- Name: idx_notifications_patient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notifications_patient ON public.notifications USING btree (patient_id, created_at DESC);


--
-- Name: notification_attempts notification_attempts_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_attempts
    ADD CONSTRAINT notification_attempts_job_id_fkey FOREIGN KEY (job_id) REFERENCES public.notification_jobs(id);


--
-- Name: notification_jobs notification_jobs_notification_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_jobs
    ADD CONSTRAINT notification_jobs_notification_id_fkey FOREIGN KEY (notification_id) REFERENCES public.notifications(id);


--
-- PostgreSQL database dump complete
--

\unrestrict y4tnd66uP9wthkkxECamYgavSjmFgIMvLEo9KC5ISOxWYgOjICL56FQyoeR0b5T

