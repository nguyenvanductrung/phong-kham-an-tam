# Nha Khoa An Tâm

Nha Khoa An Tâm là ứng dụng quản lý vận hành cho một phòng khám nha khoa. Sản phẩm tập trung vào các luồng chính từ tiếp nhận bệnh nhân và đặt lịch đến khám, lưu bệnh án và theo dõi kế hoạch điều trị. Giao diện và dữ liệu demo dùng tiếng Việt; thời gian lưu theo UTC, lịch làm việc theo múi giờ `Asia/Ho_Chi_Minh`, tiền tệ VND.

## Tính năng

- **Tài khoản và phân quyền:** đăng nhập theo vai trò lễ tân, bác sĩ, bệnh nhân và quản lý; phiên trình duyệt dùng cookie HttpOnly.
- **Hồ sơ bệnh nhân:** tạo, tìm kiếm và xem hồ sơ hành chính; liên kết tài khoản bệnh nhân với hồ sơ do nhân viên thực hiện.
- **Lịch hẹn:** xem lịch ngày/tuần; đặt, đổi, hủy, xác nhận và tiếp đón lịch. Hệ thống kiểm tra trùng bác sĩ/ghế và thời gian phục vụ.
- **Danh mục và lịch làm việc:** quản lý dịch vụ, ghế, ca bác sĩ, yêu cầu nghỉ và lịch đóng cửa; thay đổi cấu hình được kiểm tra với lịch đã đặt.
- **Khám và bệnh án:** bác sĩ phụ trách có thể mở lượt khám, lưu bản nháp, ghi nhận tình trạng răng và thủ thuật, rồi chốt bệnh án. Nội dung bệnh án đã chốt được bảo vệ khỏi chỉnh sửa trực tiếp.
- **Kế hoạch điều trị và đơn thuốc:** tạo kế hoạch với snapshot dịch vụ/giá, phê duyệt kế hoạch sau khi chốt bệnh án và phát hành đơn thuốc.
- **Tệp lâm sàng:** tải lên và xem tệp PDF/JPEG/PNG/WebP trong bucket riêng tư, có kiểm tra quyền truy cập và giới hạn kích thước.
- **Cổng bệnh nhân và email demo:** bệnh nhân xem lịch sử đã chốt, lịch hẹn và kế hoạch được phê duyệt; email demo có thể xem trong Mailpit.

## Công nghệ

| Lớp | Công nghệ |
|---|---|
| Web | Next.js 16, React 19, TypeScript, Tailwind CSS |
| Backend và API | Java 21, Spring Boot 4, Spring Cloud Gateway; REST API theo OpenAPI |
| Cơ sở dữ liệu | PostgreSQL, mỗi service sở hữu database và Flyway migration riêng |
| Phiên đăng nhập | Redis lưu session phía server; cookie trình duyệt là HttpOnly |
| Tác vụ nền | RabbitMQ, transactional outbox/inbox và worker gửi email |
| Tệp lâm sàng | MinIO với giao thức tương thích S3 và bucket private |
| Môi trường local | Docker Compose; Mailpit nhận email demo |

## Các thành phần

- `frontend/web`: giao diện web và Backend-for-Frontend (BFF).
- `backend/identity-service`: tài khoản, xác thực, phiên đăng nhập và phân quyền.
- `backend/patient-service`: hồ sơ hành chính bệnh nhân.
- `backend/scheduling-service`: lịch hẹn, danh mục và cấu hình ca làm.
- `backend/clinical-service`: bệnh án, thủ thuật, đơn thuốc, kế hoạch điều trị và tệp.
- `backend/notification-service`: thông báo và hàng đợi email.
- `backend/api-gateway`: cổng API, xác thực token, giới hạn tốc độ và định tuyến.
- `backend/common-lib`: cấu hình và thành phần dùng chung.

Mỗi service sở hữu dữ liệu riêng; các luồng liên service dùng API và sự kiện thay vì khóa ngoại chéo database. Xem [tổng quan kiến trúc](architecture/overview.md), [quyền truy cập](architecture/authorization.md) và [sơ đồ dữ liệu](database/erd.md).

## Bắt đầu

Hướng dẫn cài đặt, khởi động lần đầu, chạy lại, kiểm tra và dừng ứng dụng nằm tại **[huong-dan-chay.md](huong-dan-chay.md)**.

Ứng dụng local sau khi khởi động:

- Web: <http://localhost:3000>
- API gateway: <http://localhost:8081>
- Mailpit: <http://localhost:8025>

## Phạm vi và giới hạn

Đây là MVP demo cho một cơ sở, dùng dữ liệu tổng hợp. Chưa bao gồm thanh toán/hóa đơn, kho vật tư, CRM, labo, chấm công, KPI, SMS/Zalo và nhiều cơ sở. Một số luồng lâm sàng và ma trận quyền vẫn cần được kiểm thử E2E đầy đủ. Xem [giới hạn và roadmap](roadmap/limits.md).

Không nhập dữ liệu bệnh nhân thật vào môi trường demo. Cấu hình local và mật khẩu nằm trong `.env`; giữ file này riêng tư, không commit.
