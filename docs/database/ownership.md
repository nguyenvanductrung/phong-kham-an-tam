# Ownership dữ liệu theo service

| Bảng nguồn | Service sở hữu | Ghi chú tách |
|---|---|---|
| clinic_profile | scheduling | Cấu hình một cơ sở, timezone và giờ hoạt động |
| users, user_sessions, password_reset_tokens, roles, permissions, role_permissions, user_roles | identity | Không lưu hồ sơ hành chính bệnh nhân tại identity |
| staff_positions, staff_profiles, doctor_profiles | scheduling | Lịch dùng doctor profile projection; `user_id` là ID tham chiếu |
| patients, patient_consents | patient | `created_by`, `recorded_by` là ID, không FK identity |
| service_categories, services, rooms | scheduling | Room được diễn giải thành ghế độc quyền |
| work_shifts, staff_schedules, clinic_closures, leave_types, leave_requests, leave_balances, attendance_logs | scheduling | Không dựng attendance/KPI roadmap ở MVP |
| treatment_plans, treatment_plan_items | clinical | ID dịch vụ và bệnh nhân tham chiếu service khác, snapshot ở item |
| appointments, appointment_services | scheduling | Giữ các service snapshot dự kiến; range chống trùng bằng exclusion constraint |
| dental_records, dental_record_teeth, performed_procedures, prescriptions, prescription_items, patient_files | clinical | Tách draft/sign/amendment; object key tham chiếu MinIO riêng tư |
| notifications | notification | Jobs, attempts, inbox và outbox do notification sở hữu |
| invoices, invoice_items, einvoice_documents, payments, payment_allocations, refunds, document_sequences | roadmap billing | Chưa tạo service |
| warranties, lab_orders | roadmap aftercare/lab | Chưa tạo service |
| customer_profiles, crm_interactions, loyalty_accounts, loyalty_transactions, rewards, reward_redemptions, referral_programs, referral_logs, automation_workflows, automation_runs, nps_surveys | roadmap CRM | Chưa tạo service |
| suppliers, inventory_items, service_materials, inventory_lots, purchase_orders, purchase_order_items, inventory_transactions, procedure_inventory_usage | roadmap inventory | Chưa tạo service |
| kpi_templates, kpi_template_metrics, kpi_scores, performance_reviews | roadmap HR/KPI | Chưa tạo service |
| audit_events | service gây thay đổi | Audit nằm cùng transaction với aggregate, giữ actor snapshot |

Tài liệu tham khảo là PostgreSQL, vì vậy dự án không áp dụng chuyển đổi MySQL hay thực thi SQL nguồn. Mọi FK chỉ nằm trong database cùng service. Hãy dùng schema migrations mới trong mỗi service để phản ánh các ownership trên.

