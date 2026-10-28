#!/bin/bash
set -eu

create_database() {
  db_name="$1"
  db_user="$2"
  db_password="$3"
  psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    --set=db_name="$db_name" --set=db_user="$db_user" --set=db_password="$db_password" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_user', :'db_password') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db_name', :'db_user') \gexec
SQL
}

create_database identity_db "$IDENTITY_DB_USER" "$IDENTITY_DB_PASSWORD"
create_database patient_db "$PATIENT_DB_USER" "$PATIENT_DB_PASSWORD"
create_database scheduling_db "$SCHEDULING_DB_USER" "$SCHEDULING_DB_PASSWORD"
create_database clinical_db "$CLINICAL_DB_USER" "$CLINICAL_DB_PASSWORD"
create_database notification_db "$NOTIFICATION_DB_USER" "$NOTIFICATION_DB_PASSWORD"

