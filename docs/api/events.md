# Event contracts v1

Envelope chung: `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `aggregateId`, `aggregateVersion`, `correlationId`, `payload`. `occurredAt` là UTC. Payload chỉ chứa ID, status và timestamp cần cho tác vụ; không chứa chẩn đoán, đơn thuốc, ghi chú hay dữ liệu bệnh án.

Sự kiện: `AppointmentBooked.v1`, `AppointmentRescheduled.v1`, `AppointmentCancelled.v1`, `PatientCheckedIn.v1`, `ClinicalRecordSigned.v1`. Publisher ghi outbox trong transaction nghiệp vụ, retry với publisher confirms. Consumer ghi `processed_events(event_id)` và business effect trong cùng transaction; unique eventId, kiểm tra aggregateVersion, retry giới hạn và DLQ.

*** Add File: D:\project-learn\PhongKham\docs\runbooks\local-development.md
# Runbook local development

1. Bật Docker Desktop ở chế độ Linux containers.
2. Chạy `Copy-Item .env.example .env` và `./scripts/bootstrap.ps1`.
3. Chạy Compose từ thư mục gốc bằng `docker --context default compose --env-file .env -f infrastructure/compose/docker-compose.yml up --build -d`.
4. Xem log bằng `docker --context default compose ... logs -f <service>`; readiness ở `/actuator/health/readiness`.
5. Tắt bằng `docker --context default compose ... down`; `down -v` xoá toàn bộ dữ liệu local.

Không dùng dữ liệu khách hàng thật trong local. Lệnh backup/restore hoạt động trên các DB service và MinIO bucket; kiểm tra tệp backup trước khi xoá môi trường nguồn.

