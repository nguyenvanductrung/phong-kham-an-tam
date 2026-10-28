# ERD theo database sở hữu

Các sơ đồ là ba database tách biệt. UUID tham chiếu service khác được đánh dấu `id` và không có foreign key xuyên database. Chi tiết vật lý là các migration Flyway trong `backend/*/src/main/resources/db/migration`.

## Identity database

```mermaid
erDiagram
  USERS ||--o{ USER_ROLES : assigned
  ROLES ||--o{ USER_ROLES : grants
  ROLES ||--o{ ROLE_PERMISSIONS : contains
  PERMISSIONS ||--o{ ROLE_PERMISSIONS : maps
  USERS ||--o{ REFRESH_TOKENS : rotates
  USERS { uuid id PK string email UK string password_hash string status }
  ROLES { string code PK string name }
  PERMISSIONS { string code PK string description }
  USER_ROLES { uuid user_id FK string role_code FK }
  ROLE_PERMISSIONS { string role_code FK string permission_code FK }
  REFRESH_TOKENS { uuid id PK uuid user_id FK uuid family_id bytes token_hash datetime expires_at datetime revoked_at }
```

## Patient database

```mermaid
erDiagram
  PATIENTS ||--o{ PATIENT_CONSENTS : records
  PATIENTS ||--o| PATIENT_ACCOUNT_LINKS : linked_account
  PATIENTS ||--o{ PATIENT_ACCESS_GRANTS : permits
  PATIENTS { uuid id PK string patient_code UK string full_name string phone string allergies string medical_history }
  PATIENT_CONSENTS { uuid id PK uuid patient_id FK string consent_type string status datetime granted_at datetime revoked_at }
  PATIENT_ACCOUNT_LINKS { uuid patient_id PK_FK uuid user_id UK datetime linked_at }
  PATIENT_ACCESS_GRANTS { uuid patient_id PK_FK uuid doctor_id PK uuid appointment_id PK bigint aggregate_version datetime expires_at datetime revoked_at }
  PROCESSED_EVENTS { uuid event_id PK uuid aggregate_id bigint aggregate_version }
```

## Scheduling database

```mermaid
erDiagram
  DOCTORS ||--o{ DOCTOR_WORKING_HOURS : works
  DOCTORS ||--o{ DOCTOR_LEAVE : requests
  DOCTORS ||--o{ APPOINTMENTS : assigned
  CHAIRS ||--o{ APPOINTMENTS : reserves
  APPOINTMENTS ||--|{ APPOINTMENT_SERVICES : snapshots
  CLINIC_SETTINGS { int id PK string timezone string currency int no_show_grace_minutes }
  DOCTORS { uuid user_id PK string display_name string license_number UK }
  DOCTOR_WORKING_HOURS { uuid id PK uuid doctor_id FK int day_of_week time starts_at time ends_at date effective_from date effective_until }
  DOCTOR_LEAVE { uuid id PK uuid doctor_id FK datetime starts_at datetime ends_at string status }
  CHAIRS { uuid id PK string name UK boolean is_active }
  SERVICES { uuid id PK string code UK string name numeric price int duration_minutes }
  CLINIC_CLOSURES { uuid id PK datetime starts_at datetime ends_at }
  APPOINTMENTS { uuid id PK string appointment_number UK uuid patient_id uuid doctor_id FK uuid chair_id FK datetime scheduled_at int duration_minutes string status bigint version }
  APPOINTMENT_SERVICES { uuid id PK uuid appointment_id FK uuid service_id string title_snapshot numeric estimated_unit_price }
  IDEMPOTENCY_RECORDS { uuid actor_id PK string idempotency_key PK bytes request_hash uuid appointment_id FK }
```

## Clinical database

```mermaid
erDiagram
  CLINICAL_RECORDS ||--o{ DENTAL_RECORD_TEETH : describes
  CLINICAL_RECORDS ||--o{ PERFORMED_PROCEDURES : contains
  CLINICAL_RECORDS ||--o{ PRESCRIPTIONS : issues
  CLINICAL_RECORDS ||--o| TREATMENT_PLANS : plans
  TREATMENT_PLANS ||--|{ TREATMENT_PLAN_ITEMS : contains
  PRESCRIPTIONS ||--|{ PRESCRIPTION_ITEMS : contains
  CLINICAL_RECORDS ||--o{ PATIENT_FILES : attaches
  CLINICAL_RECORDS ||--o{ CLINICAL_RECORDS : amends
  CLINICAL_RECORDS { uuid id PK uuid patient_id uuid appointment_id uuid doctor_id datetime visit_at string status bigint version uuid amends_record_id FK }
  DENTAL_RECORD_TEETH { uuid id PK uuid record_id FK int tooth_number string condition_code }
  PERFORMED_PROCEDURES { uuid id PK uuid patient_id uuid record_id FK uuid doctor_id uuid service_id string title_snapshot numeric quantity numeric unit_price numeric net_amount }
  PRESCRIPTIONS { uuid id PK uuid patient_id uuid record_id FK uuid doctor_id string status datetime issued_at }
  PRESCRIPTION_ITEMS { uuid id PK uuid prescription_id FK string medication_name string dosage string route string frequency }
  TREATMENT_PLANS { uuid id PK uuid patient_id uuid clinical_record_id FK uuid doctor_id string title string status bigint version }
  TREATMENT_PLAN_ITEMS { uuid id PK uuid plan_id FK int sequence_no uuid service_id string title_snapshot numeric quantity numeric unit_price }
  PATIENT_FILES { uuid id PK uuid patient_id uuid record_id FK string object_key UK string mime_type bigint byte_size }
```

## Notification database

```mermaid
erDiagram
  NOTIFICATIONS ||--o{ NOTIFICATION_JOBS : queues
  NOTIFICATION_JOBS ||--o{ NOTIFICATION_ATTEMPTS : records
  NOTIFICATIONS { uuid id PK uuid patient_id string channel string template_code string status uuid idempotency_key UK }
  NOTIFICATION_JOBS { uuid id PK uuid notification_id FK uuid event_id UK string recipient string status int attempts datetime next_attempt_at }
  NOTIFICATION_ATTEMPTS { uuid id PK uuid job_id FK int attempt_number string outcome datetime attempted_at }
  PROCESSED_EVENTS { uuid event_id PK uuid aggregate_id bigint aggregate_version }
```
