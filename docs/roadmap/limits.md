# Phạm vi MVP và giới hạn hiện tại

## Đã có luồng chạy thật

- Đăng nhập bằng JWT/JWKS, refresh token rotation, BFF với cookie opaque HttpOnly và session server-side trong Redis.
- Nhân viên tìm/tạo hồ sơ hành chính, phân trang lịch, xem ngày/tuần, tạo/đổi/hủy lịch, xác nhận và tiếp đón. Bệnh nhân đã liên kết hồ sơ có thể xem, đặt, đổi và hủy lịch của mình.
- Kiểm tra xung đột lịch ở PostgreSQL bằng exclusion constraint; booking và command có idempotency key. Bác sĩ phụ trách mở lượt khám, lưu nháp, ghi tình trạng răng/thủ thuật, chốt bệnh án, tạo đơn thuốc sau chốt; trigger PostgreSQL bảo vệ nội dung đã chốt/phát hành.
- Bệnh nhân chỉ đọc lịch sử đã chốt và đơn đã phát hành. Tài khoản PATIENT chỉ được nối hồ sơ bởi nhân viên có quyền, không suy ra quyền sở hữu từ số điện thoại.
- Outbox/inbox, consumer thông báo email, audit cùng transaction, Compose, seed dev lặp lại được, backup/restore và E2E cross-role đã được dựng.

## Chưa triển khai hoặc chưa nghiệm thu

- Clinical API và UI bác sĩ/bệnh nhân đã hỗ trợ upload/list/download tệp PDF/JPEG/PNG/WebP lên bucket private, kiểm tra bác sĩ phụ trách hoặc bệnh nhân sở hữu bệnh án đã chốt, giới hạn 20 MiB, nhận diện chữ ký định dạng, checksum SHA-256 và object key ngẫu nhiên; chưa có quét mã độc hoặc luồng presigned URL. Download được stream sau khi kiểm quyền.
- Clinical API và UI bác sĩ/bệnh nhân đã có treatment plan với snapshot dịch vụ/giá, thứ tự hạng mục và phê duyệt bất biến sau khi bệnh án đã chốt; chưa theo dõi trạng thái thực hiện từng hạng mục, phiên điều trị và quyết toán kế hoạch.
- Đã có màn/API quản lý dịch vụ, ghế, ca làm bác sĩ, yêu cầu và duyệt nghỉ, lịch đóng cửa; thao tác cấu hình khóa phối hợp với booking, không cho duyệt nghỉ/đóng cửa/tắt ghế/xóa ca nếu làm xung đột lịch đã đặt. Chưa có giao diện cập nhật hồ sơ bác sĩ hoặc quản lý nhiều cơ sở.
- Chi tiết bệnh nhân đã hiển thị tối đa 100 lịch hẹn gần nhất. Chưa có timeline gộp sự kiện hành chính, lịch hẹn và lâm sàng; dữ liệu bệnh án tiếp tục ở màn hình riêng theo vai trò để lễ tân không thấy nội dung chuyên môn. TypeScript types và operation client được sinh/định kiểu từ OpenAPI cho các luồng bệnh nhân, tài khoản, lịch hẹn và cấu hình danh mục; các màn lâm sàng còn dùng adapter generic và contract phản hồi của một số endpoint lâm sàng vẫn cần bổ sung.
- Chưa có luồng đổi mật khẩu/quên mật khẩu/mời tài khoản. Tạo tài khoản dùng mật khẩu khởi tạo do quản lý đặt trực tiếp.
- Bộ test chưa phủ hết ma trận yêu cầu: bệnh nhân A/B truy cập chéo, bác sĩ không phụ trách, mọi nhánh idempotency/reschedule, retry outbox sau RabbitMQ phục hồi và quyền lâm sàng theo tài nguyên. Hai test mới về inbox xử lý lặp/sai thứ tự và chống tạo trùng email job đã chạy thật bằng Testcontainers, đều đạt (1 test ở patient-service, 1 ở notification-service). Lần `mvnw -B verify` trước đó cũng đạt: 17 unit test state machine, 7 test PostgreSQL lịch và 4 test PostgreSQL bất biến bệnh án.
- Lượt kiểm tra giao diện thực tế đã xác nhận đăng nhập/đăng xuất lễ tân và bệnh nhân, danh sách/chi tiết bệnh nhân, form lịch hẹn, cổng bệnh nhân và màn kết quả khám. Chưa submit thao tác ghi mới trong database demo; các luồng đặt/đổi/hủy lịch, lập/chốt bệnh án, phát hành đơn thuốc và tải tệp vẫn cần E2E tự động với dữ liệu thử có thể dọn riêng.
- Docker Compose đã dựng lại image backend và web, `up --wait` đạt; toàn bộ service `phongkham` hiện healthy (MinIO đang chạy không có healthcheck) và các service LMS đã được khởi động lại. Hai kiểm tra mới trong môi trường này cũng sửa được bind host của Next.js, favicon 404, Redis lazy connection và truy vấn portal bị vượt giới hạn 31 ngày của API. Tài khoản/volume demo được giữ nguyên.
- Docker Desktop hiện dùng WSL memory limit 6 GB. Khi cả stack LMS, phòng khám và database khác cùng chạy, thêm tác vụ build có thể làm engine mất phản hồi. Lần triển khai này phải tạm dừng 18 service ứng dụng LMS trong lúc build; tất cả đã được khởi động lại và healthy. Khi build lớn, cần chạy lần lượt hoặc giảm tải trước để tránh lặp lại sự cố.

## Ngoài MVP đã chốt

Billing/hóa đơn điện tử/thanh toán/hoàn tiền; kho vật tư/lô/hạn dùng; CRM/tích điểm/chăm sóc tự động; bảo hành; labo; chấm công; KPI; tích hợp SMS/Zalo; triển khai nhiều cơ sở.

## Giới hạn môi trường và dữ liệu

MVP hỗ trợ một cơ sở. Tài liệu tham khảo được yêu cầu mô tả MySQL, nhưng file Word thực tế chứa PostgreSQL DDL; chi tiết đối chiếu nằm trong `docs/database/postgres-migration-notes.md`. Cần xác nhận hệ DB gốc trước khi coi chuyển đổi schema là hoàn tất.

Mailpit và tài khoản seed chỉ dành cho demo. Không đưa dữ liệu bệnh nhân thật vào môi trường này. Compose demo dùng thông tin xác thực MinIO gốc để đơn giản hóa khởi động; production cần user/policy giới hạn bucket cho clinical-service, quét mã độc, secret manager/KMS, TLS, backup ngoài máy, chính sách retention, luồng khôi phục và diễn tập phục hồi.
