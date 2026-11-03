# Kết quả kiểm tra DAO và CRUD — 04/11/2026

Phạm vi: tách SQL/mapping vào DAO JDBC, khai báo interface nghiệp vụ, bổ sung CRUD bệnh nhân,
lưu trữ/khôi phục và guard chống đặt lịch đồng thời với thao tác xóa/lưu trữ.

| Kiểm tra | Kết quả |
|---|---|
| Maven `verify`, JDK 21, Docker Desktop | 41 test, 0 failure, 0 error, 0 skipped |
| Frontend TypeScript `tsc --noEmit` | Thành công |
| Frontend ESLint | Thành công |
| Docker Compose build + `up --wait` | Backend, gateway, web và dịch vụ phụ trợ khởi động thành công |
| E2E CRUD trên Chromium | Tạo, sửa, lưu trữ, lọc ARCHIVED, khôi phục, xóa hồ sơ chưa sử dụng đều thành công |
| E2E khám nhiều vai trò trên Chromium | Đặt/tiếp đón/khám/chốt bệnh án, tệp, kế hoạch, đơn thuốc, liên kết tài khoản đều thành công |
| E2E lịch sử sau lưu trữ | Từ chối xóa hồ sơ đã liên kết; bệnh nhân vẫn xem bệnh án, đơn thuốc và tệp sau lưu trữ; quản lý khôi phục thành công |

Testcontainers dùng PostgreSQL tạm độc lập với dữ liệu demo. E2E tạo dữ liệu tổng hợp riêng
với tên/email duy nhất; hồ sơ CRUD chưa sử dụng được xóa, hồ sơ đã khám được giữ lịch sử.
Hai luồng E2E đã được xác nhận qua các lượt chạy; lượt chạy cuối của luồng khám mất 10,7 giây.

Máy kiểm tra có Chromium đầy đủ, thiếu binary headless-shell mặc định của Playwright. Đã dùng
`PLAYWRIGHT_CHROMIUM_CHANNEL=chromium` để chạy trình duyệt có sẵn. Các helper test cũ cũng được
sửa để chọn một nút đăng xuất, chờ availability đúng ngày và kiểm tra trạng thái liên kết đã ổn định.

Các giới hạn phối hợp liên service và cách khôi phục khi compensation thất bại được mô tả tại
[DAO và vòng đời hồ sơ](../architecture/dao-and-patient-lifecycle.md).

Không suy ra điểm chấm từ các kết quả trên; phần export SQL của tiêu chí Database được đánh giá riêng.
