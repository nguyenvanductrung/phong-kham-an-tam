# Tóm tắt nghiệp vụ từ tài liệu tham khảo

## Phạm vi dữ liệu

Nguồn có các nhóm dữ liệu: cấu hình cơ sở; tài khoản, vai trò và quyền; hồ sơ nhân viên/bác sĩ; bệnh nhân và consent; danh mục dịch vụ, ghế; ca làm, nghỉ, đóng cửa; kế hoạch điều trị; lịch hẹn và dịch vụ dự kiến; bệnh án, răng, thủ thuật thực tế; đơn thuốc; tệp; hóa đơn/thanh toán/hoàn tiền; CRM/tích điểm; kho; KPI; audit và view vận hành.

## Quy tắc rút ra

- Thời điểm dùng `TIMESTAMPTZ`; ngày và giờ ca là giờ địa phương. Ghế/phòng là tài nguyên độc quyền. Tiền dùng số thập phân chính xác.
- Lịch hẹn liên kết bệnh nhân, bác sĩ, ghế, dịch vụ dự kiến và có thể tham chiếu kế hoạch điều trị. Thủ thuật thực tế phải có liên kết đến hồ sơ khám/dịch vụ và lưu snapshot mô tả/giá.
- Bệnh án có patient, doctor, appointment, thời điểm khám, triệu chứng, tiền sử, khám, chẩn đoán và hướng điều trị; có dữ liệu theo răng, trạng thái ký, người ký và liên kết bản bổ sung.
- Consent có loại, trạng thái, thời điểm cấp/thu hồi, kênh, phiên bản chính sách và bằng chứng. Thông báo có kênh, trạng thái, lịch gửi, idempotency key và lỗi gửi.
- Nguồn chứa billing, CRM, kho, bảo hành, labo, chấm công và KPI; các mục ngoài phạm vi MVP được đưa vào roadmap, không tạo service.

## Mâu thuẫn và phần còn thiếu

Tệp Word được cung cấp ghi “PostgreSQL 15+” và dùng cú pháp/kiểu/extension PostgreSQL; điều này mâu thuẫn với mô tả đầu vào rằng tài liệu được thiết kế cho MySQL. Vì vậy dự án chỉ trích xuất nghiệp vụ và tự tạo schema PostgreSQL theo ownership mới; không chuyển/chạy DDL nguồn. Tài liệu gộp nhiều nghiệp vụ trong một DB, nên quan hệ liên service được chuyển thành ID/event/API. Các quyết định trạng thái lịch, slot 15 phút, quyền, bất biến bệnh án và ngưỡng hủy lấy theo prompt dự án vì chúng cụ thể hơn nguồn.

