# Tổng quan kiến trúc

```mermaid
flowchart LR
  Browser -->|HttpOnly session| Web[Next.js BFF]
  Web -->|Bearer access token| Gateway[API Gateway]
  Gateway --> Identity
  Gateway --> Patient
  Gateway --> Scheduling
  Gateway --> Clinical
  Gateway --> Notification
  Identity --> IDDB[(identity_db)]
  Patient --> PTDB[(patient_db)]
  Scheduling --> SCDB[(scheduling_db)]
  Clinical --> CLDB[(clinical_db)]
  Notification --> NTDB[(notification_db)]
  Identity --> Redis[(Redis sessions)]
  Scheduling --> Rabbit[(RabbitMQ)]
  Clinical --> Rabbit
  Notification --> Rabbit
  Notification --> Mailpit
  Clinical --> MinIO
```

Mỗi service sở hữu một database, migration và DB user. ID tham chiếu không tạo FK chéo service. Gateway xác minh chữ ký JWT/JWKS, giới hạn tốc độ và correlation ID; service tự kiểm issuer/audience/expiry/role. BFF giữ access/refresh token server-side trong Redis và phát opaque cookie HttpOnly cho trình duyệt.

```mermaid
sequenceDiagram
  actor User as Người dùng
  participant UI as Next.js BFF
  participant G as Gateway
  participant S as Scheduling
  participant DB as scheduling_db
  participant Q as RabbitMQ
  User->>UI: Đặt lịch + Idempotency-Key + CSRF
  UI->>G: POST /api/v1/appointments, Bearer
  G->>S: JWT đã xác thực + correlation ID
  S->>DB: Transaction, khóa cấu hình, validate ca/ghế, insert appointment + outbox
  DB-->>S: Commit hoặc exclusion conflict
  S-->>G: 201 hoặc 409 APPOINTMENT_CONFLICT
  G-->>UI: Kết quả
  S->>Q: Outbox publisher, publisher confirm
```

```mermaid
sequenceDiagram
  actor Doctor as Bác sĩ
  participant C as Clinical
  participant DB as clinical_db
  participant Q as RabbitMQ
  participant S as Scheduling
  Doctor->>C: Chốt bản nháp
  C->>DB: Transaction + audit + outbox; ký chỉ bác sĩ phụ trách
  DB-->>C: Commit, bản bệnh án bất biến
  C->>Q: ClinicalRecordSigned.v1 (at-least-once)
  Q->>S: Inbox eventId + aggregateVersion
  S->>S: Kiểm appointment/doctor, đánh dấu COMPLETED idempotently
```

