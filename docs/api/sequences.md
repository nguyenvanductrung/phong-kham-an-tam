# Luồng xuyên service

## Đặt lịch

```mermaid
sequenceDiagram
  actor Staff as Lễ tân / Bệnh nhân
  participant Web as Next.js BFF
  participant Gateway as API Gateway
  participant Schedule as Scheduling
  participant Patient as Patient
  participant DB as scheduling_db
  participant Rabbit as RabbitMQ
  participant Notify as Notification
  Staff->>Web: POST /api/v1/appointments + Idempotency-Key
  Web->>Gateway: Bearer token từ session server-side
  Gateway->>Schedule: Xác minh JWT, forward request
  Schedule->>Patient: Xác minh hồ sơ (và liên kết nếu PATIENT)
  Patient-->>Schedule: Hồ sơ hợp lệ
  Schedule->>DB: Lock clinic settings; kiểm ca/nghỉ/đóng cửa; insert appointment + snapshot + outbox + audit
  DB-->>Schedule: Commit; exclusion GiST phân xử xung đột
  Schedule-->>Web: 201 hoặc Problem Details 409
  Schedule->>Rabbit: Outbox worker publish sau commit, publisher confirm
  Rabbit->>Notify: AppointmentBooked.v1
  Notify->>Notify: Inbox dedupe + email job trong một transaction
  Notify-->>Rabbit: Ack sau commit
  Notify->>Notify: Mail worker thử gửi và ghi attempt
```

## Chốt bệnh án

```mermaid
sequenceDiagram
  actor Doctor as Bác sĩ phụ trách
  participant Web as Next.js BFF
  participant Gateway as API Gateway
  participant Clinical as Clinical
  participant CDB as clinical_db
  participant Rabbit as RabbitMQ
  participant Schedule as Scheduling
  participant SDB as scheduling_db
  Doctor->>Web: POST /records/{id}/sign?version=n
  Web->>Gateway: Bearer token từ session server-side
  Gateway->>Clinical: Xác minh issuer, audience, chữ ký
  Clinical->>CDB: UPDATE DRAFT->SIGNED có doctor_id + version; immutable DB trigger; audit + outbox cùng transaction
  CDB-->>Clinical: Commit
  Clinical-->>Web: Bệnh án đã chốt
  Clinical->>Rabbit: Outbox publisher confirm
  Rabbit->>Schedule: ClinicalRecordSigned.v1
  Schedule->>SDB: Inbox dedupe + check appointment/doctor/version + COMPLETED + audit trong transaction
  Schedule-->>Rabbit: Ack sau commit
  Note over Clinical,Schedule: Nhất quán cuối cùng; retry an toàn nhờ eventId, aggregateVersion và inbox.
```
