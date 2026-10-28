# Ghi chú chuyển schema và tách database

## Đối chiếu tài liệu nguồn

Brief ban đầu gọi tài liệu Word là MySQL, nhưng file thực tế `D:\Dental clinic database foundation.docx` chứa PostgreSQL DDL (UUID, `TIMESTAMPTZ`, `JSONB`, `tstzrange`, trigger PL/pgSQL, `ON CONFLICT`). Vì vậy không chạy SQL trong tài liệu; các migration hiện tại được viết mới theo ranh giới service và dùng PostgreSQL.

## Ánh xạ cơ chế khi tách database

| Cơ chế/tài liệu gốc | Khi tách service |
|---|---|
| FK giữa bảng cùng miền | Giữ trong database sở hữu; không tạo FK đến database khác |
| FK đến `users` hoặc `services` khác miền | Chuyển thành UUID tham chiếu; xác minh đồng bộ qua API/sự kiện, lưu snapshot khi cần |
| JOIN/view đọc dữ liệu nhiều miền | Bỏ view dùng chung; gọi API hoặc duy trì projection qua Inbox |
| Trigger sửa dữ liệu nghiệp vụ giữa domains | Chuyển nghiệp vụ thành command trong service sở hữu; side effect xuyên miền dùng transactional outbox |
| `ENUM`/`SET` MySQL | Dùng `TEXT` + `CHECK`, hoặc lookup table khi cần quản trị |
| `DATETIME` MySQL | Dùng `TIMESTAMPTZ`; lưu instant UTC, hiển thị theo `Asia/Ho_Chi_Minh` |
| `DECIMAL` MySQL | Dùng `NUMERIC(p,s)` và `BigDecimal`; không dùng floating point |
| `AUTO_INCREMENT` | Dùng UUID (`gen_random_uuid`) để tránh cấp số tập trung; số hồ sơ hiển thị tạo riêng |
| JSON/JSONB payload | Dùng `JSONB` chỉ cho metadata/audit/event; dữ liệu cần truy vấn vẫn là cột chuẩn hóa |
| Trigger bất biến bệnh án/đơn thuốc | Giữ trigger PostgreSQL trong `clinical_db`, đồng thời kiểm tra tại application layer |
| Trigger trùng lịch | Thay bằng `tstzrange` + GiST exclusion constraints cho bác sĩ, bệnh nhân và ghế |
| Seed/autonumber/trình tự dùng chung | Sinh ID và mã trong service sở hữu; không dựa vào cross-database sequence |

## Chưa đưa vào MVP

Billing, kho, CRM, bảo hành, labo, chấm công, KPI chỉ được ánh xạ trong `ownership.md`; không có migration/service placeholder. Khi bổ sung, xác định service sở hữu rồi tạo migration và hợp đồng tích hợp mới.
