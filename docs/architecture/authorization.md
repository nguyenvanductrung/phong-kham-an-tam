# Ma trận quyền rút gọn

| Hành động | SUPER_ADMIN | CLINIC_MANAGER | DOCTOR | RECEPTIONIST | ASSISTANT | PATIENT |
|---|---|---|---|---|---|---|
| Quản lý tài khoản/quyền | Có | Theo phân quyền | Không | Không | Không | Không |
| Sửa dịch vụ/ghế/ca; ghi nhận đóng cửa; duyệt nghỉ | Có | Có | Không | Không | Không | Không |
| Xem/sửa hồ sơ hành chính | Có | Có | Bệnh nhân được phân công | Có | Theo phân công | Chỉ của mình |
| Đặt/đổi lịch | Có | Có | Lịch của mình | Có | Hỗ trợ | Của mình, theo chính sách |
| Xem nội dung bệnh án | Không mặc định | Chỉ khi cấp quyền lâm sàng | Ca được phân công | Không | Ca được phân công | Kết quả đã công bố của mình |
| Tạo/chốt bệnh án | Không | Không ký thay | Bác sĩ phụ trách | Không | Không | Không |
| Phát hành đơn thuốc | Không | Không | Bác sĩ phụ trách, sau khi chốt | Không | Không | Không |
| Kế hoạch điều trị | Không | Không | Bác sĩ phụ trách; duyệt sau khi bệnh án chốt | Không | Không | Chỉ qua kết quả đã công bố |
| Tải tệp bệnh án | Không | Không | Bác sĩ phụ trách | Không | Không | Chỉ tệp trong bệnh án đã chốt của mình |

Mọi kiểm tra tài nguyên chạy ở backend. Role ở giao diện chỉ là UX; `patientId`/`doctorId` do client gửi không chứng minh quyền. Lễ tân chỉ dùng API hồ sơ hành chính, không gọi API lâm sàng.

