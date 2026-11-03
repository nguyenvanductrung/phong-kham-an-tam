-- File SQL export của identity_db (dịch vụ identity-service), Phòng khám An Tâm.
-- Tạo từ các migration Flyway trong backend/identity-service/src/main/resources/db/migration (commit 39db848, 04/11/2026).
-- Gồm cấu trúc bảng, ràng buộc, chỉ mục, trigger, hàm. Dữ liệu demo do ứng dụng tự tạo khi khởi động nên không có trong file.
-- Cách nạp: CREATE DATABASE identity_db; rồi chạy  psql -d identity_db -f identity_db.sql

--
-- PostgreSQL database dump
--

\restrict v73eNJqlygRKZvGdEJQ5MwUYhmdOXkecxJ9tl6o0w8UMxnT47OjPoaAC4EGousZ

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
-- Name: identity_audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.identity_audit_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    actor_user_id uuid,
    actor_label text NOT NULL,
    action text NOT NULL,
    target_user_id uuid NOT NULL,
    request_id text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL
);


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    code text NOT NULL,
    description text NOT NULL
);


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    family_id uuid NOT NULL,
    user_id uuid NOT NULL,
    token_hash bytea NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    revoked_at timestamp with time zone,
    CONSTRAINT refresh_tokens_check CHECK ((expires_at > created_at)),
    CONSTRAINT refresh_tokens_token_hash_check CHECK ((octet_length(token_hash) = 32))
);


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role_code text NOT NULL,
    permission_code text NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    code text NOT NULL,
    name text NOT NULL,
    is_system boolean DEFAULT true NOT NULL
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    user_id uuid NOT NULL,
    role_code text NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    assigned_by uuid
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email text NOT NULL,
    password_hash text NOT NULL,
    full_name text NOT NULL,
    status text DEFAULT 'ACTIVE'::text NOT NULL,
    failed_login_count integer DEFAULT 0 NOT NULL,
    locked_until timestamp with time zone,
    last_login_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT users_failed_login_count_check CHECK ((failed_login_count >= 0)),
    CONSTRAINT users_status_check CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'DISABLED'::text])))
);


--
-- Data for Name: identity_audit_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.identity_audit_events (id, occurred_at, actor_user_id, actor_label, action, target_user_id, request_id, metadata) FROM stdin;
\.


--
-- Data for Name: permissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.permissions (code, description) FROM stdin;
\.


--
-- Data for Name: refresh_tokens; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.refresh_tokens (id, family_id, user_id, token_hash, created_at, expires_at, used_at, revoked_at) FROM stdin;
\.


--
-- Data for Name: role_permissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.role_permissions (role_code, permission_code) FROM stdin;
\.


--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.roles (code, name, is_system) FROM stdin;
\.


--
-- Data for Name: user_roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_roles (user_id, role_code, assigned_at, assigned_by) FROM stdin;
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (id, email, password_hash, full_name, status, failed_login_count, locked_until, last_login_at, created_at, updated_at) FROM stdin;
\.


--
-- Name: identity_audit_events identity_audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_audit_events
    ADD CONSTRAINT identity_audit_events_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (code);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_code, permission_code);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (code);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_code);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_identity_audit_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_identity_audit_target ON public.identity_audit_events USING btree (target_user_id, occurred_at DESC);


--
-- Name: idx_refresh_tokens_family; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_family ON public.refresh_tokens USING btree (family_id);


--
-- Name: idx_refresh_tokens_user_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_user_expiry ON public.refresh_tokens USING btree (user_id, expires_at);


--
-- Name: uq_users_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_users_email ON public.users USING btree (lower(email));


--
-- Name: refresh_tokens refresh_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: role_permissions role_permissions_permission_code_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_permission_code_fkey FOREIGN KEY (permission_code) REFERENCES public.permissions(code) ON DELETE CASCADE;


--
-- Name: role_permissions role_permissions_role_code_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_role_code_fkey FOREIGN KEY (role_code) REFERENCES public.roles(code) ON DELETE CASCADE;


--
-- Name: user_roles user_roles_role_code_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_role_code_fkey FOREIGN KEY (role_code) REFERENCES public.roles(code);


--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict v73eNJqlygRKZvGdEJQ5MwUYhmdOXkecxJ9tl6o0w8UMxnT47OjPoaAC4EGousZ

