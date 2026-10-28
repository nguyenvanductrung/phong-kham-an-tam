# Trạng thái lịch hẹn

Mọi thời đoạn dùng khoảng nửa mở `[start, end)`. Đặt lịch và đổi lịch kiểm tra xung đột bằng exclusion constraint PostgreSQL; slot hiển thị trên giao diện chỉ có tính tham khảo.

| Trạng thái hiện tại | Lệnh / sự kiện | Vai trò | Điều kiện | Trạng thái tiếp theo |
|---|---|---|---|---|
| `BOOKED` | `CONFIRM` | `RECEPTIONIST`, `CLINIC_MANAGER`, `SUPER_ADMIN` | Lịch đang được đặt | `CONFIRMED` |
| `BOOKED` | `CANCEL` | `PATIENT`, `RECEPTIONIST`, `CLINIC_MANAGER`, `SUPER_ADMIN` | Bệnh nhân hủy trước giờ hẹn ít nhất 2 giờ; nhân viên ghi lý do | `CANCELLED` |
| `BOOKED` | `MARK_NO_SHOW` | `RECEPTIONIST`, `CLINIC_MANAGER`, `SUPER_ADMIN` | Đã qua giờ hẹn cộng ngưỡng no-show cấu hình | `NO_SHOW` |
| `CONFIRMED` | `CHECK_IN` | `RECEPTIONIST`, `ASSISTANT`, `CLINIC_MANAGER`, `SUPER_ADMIN` | Lịch đã xác nhận | `ARRIVED` |
| `CONFIRMED` | `CANCEL` | `PATIENT`, `RECEPTIONIST`, `CLINIC_MANAGER`, `SUPER_ADMIN` | Như trên | `CANCELLED` |
| `CONFIRMED` | `MARK_NO_SHOW` | `RECEPTIONIST`, `CLINIC_MANAGER`, `SUPER_ADMIN` | Như trên | `NO_SHOW` |
| `ARRIVED` | `START` | `DOCTOR` | Người gọi phải là bác sĩ được phân công | `IN_PROGRESS` |
| `IN_PROGRESS` | `ClinicalRecordSigned.v1` | Bất đồng bộ từ clinical-service | Event hợp lệ, appointment/patient/doctor khớp; xử lý inbox idempotently | `COMPLETED` |

Mọi lệnh khác bị từ chối bằng `409 INVALID_APPOINTMENT_TRANSITION`. Không có lệnh đặt trạng thái tùy ý. Hủy và no-show giữ lịch sử, đồng thời rời tập trạng thái được tính vào ràng buộc trùng lịch. Bác sĩ không thể ký bệnh án của lượt khám được phân công cho bác sĩ khác.

Kiểm thử bảng chuyển trạng thái nằm trong `AppointmentStateMachineTest`; kiểm thử PostgreSQL thật cho xung đột và rollback đổi lịch nằm trong `AppointmentExclusionIntegrationTest`.
