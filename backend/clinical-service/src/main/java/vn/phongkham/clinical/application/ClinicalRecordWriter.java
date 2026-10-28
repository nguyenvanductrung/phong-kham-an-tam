package vn.phongkham.clinical.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.clinical.api.ClinicalModels;
import vn.phongkham.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class ClinicalRecordWriter {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public ClinicalRecordWriter(JdbcTemplate jdbc,JsonMapper json){this.jdbc=jdbc;this.json=json;}

    @Transactional
    public ClinicalModels.RecordResponse createFromAppointment(SchedulingLookupClient.Appointment appointment,UUID actor,String correlationId,String complaint){
        if(!"IN_PROGRESS".equals(appointment.status()))throw new ApiException(HttpStatus.CONFLICT,"APPOINTMENT_NOT_IN_PROGRESS","Chỉ lượt khám đang thực hiện mới tạo được bệnh án.");
        if(!appointment.doctorId().equals(actor))throw new ApiException(HttpStatus.FORBIDDEN,"DOCTOR_NOT_ASSIGNED","Chỉ bác sĩ phụ trách mới được tạo bệnh án.");
        UUID id=UUID.randomUUID();
        int inserted=jdbc.update("INSERT INTO clinical_records(id,record_number,patient_id,appointment_id,doctor_id,visit_at,chief_complaint) VALUES (?,?,?,?,?,?,?) ON CONFLICT (appointment_id) WHERE amends_record_id IS NULL DO NOTHING",
                id,"HS-"+id.toString().substring(0,8).toUpperCase(),appointment.patientId(),appointment.id(),appointment.doctorId(),databaseTime(appointment.scheduledAt()),blank(complaint));
        if(inserted==0){UUID existing=jdbc.queryForObject("SELECT id FROM clinical_records WHERE appointment_id=? AND amends_record_id IS NULL",UUID.class,appointment.id());return find(existing);}
        audit(actor,"DOCTOR","CLINICAL_RECORD_CREATED",id,correlationId);
        return find(id);
    }

    @Transactional
    public ClinicalModels.RecordResponse saveDraft(UUID id,ClinicalModels.SaveDraftRequest request,UUID actor,String correlationId){
        int count=jdbc.update("""
                UPDATE clinical_records SET chief_complaint=?,medical_history=?,examination_notes=?,diagnosis=?,treatment_notes=?,follow_up_at=?,version=version+1,updated_at=now()
                WHERE id=? AND doctor_id=? AND status='DRAFT' AND version=?
                """,blank(request.chiefComplaint()),blank(request.medicalHistory()),blank(request.examinationNotes()),blank(request.diagnosis()),
                blank(request.treatmentNotes()),request.followUpAt()==null?null:databaseTime(request.followUpAt()),id,actor,request.version());
        if(count==0) {
            if(!exists(id))throw notFound();
            if(!isOwner(id,actor))throw forbidden();
            throw new ApiException(HttpStatus.CONFLICT,"STALE_RECORD_VERSION","Bệnh án đã được cập nhật hoặc đã chốt. Hãy tải lại trước khi lưu.");
        }
        audit(actor,"DOCTOR","CLINICAL_RECORD_DRAFT_UPDATED",id,correlationId);
        return find(id);
    }

    @Transactional
    public ClinicalModels.RecordResponse createAmendment(UUID originalId,String reason,UUID actor,String correlationId){
        var original=jdbc.query("SELECT id,patient_id,appointment_id,doctor_id,visit_at,status FROM clinical_records WHERE id=? FOR UPDATE",
                (rs,n)->new Original(rs.getObject("id",UUID.class),rs.getObject("patient_id",UUID.class),rs.getObject("appointment_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getTimestamp("visit_at").toInstant(),rs.getString("status")),originalId).stream().findFirst().orElseThrow(this::notFound);
        if(!original.doctorId().equals(actor))throw forbidden();
        if(!"SIGNED".equals(original.status()))throw new ApiException(HttpStatus.CONFLICT,"ORIGINAL_RECORD_NOT_SIGNED","Chỉ bệnh án đã chốt mới được bổ sung.");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO clinical_records(id,record_number,patient_id,appointment_id,doctor_id,visit_at,amends_record_id,amendment_reason) VALUES (?,?,?,?,?,?,?,?)",
                id,"HS-"+id.toString().substring(0,8).toUpperCase(),original.patientId(),original.appointmentId(),original.doctorId(),databaseTime(Instant.now()),originalId,reason.trim());
        audit(actor,"DOCTOR","CLINICAL_RECORD_AMENDMENT_CREATED",id,correlationId);
        return find(id);
    }

    @Transactional
    public ClinicalModels.ToothResponse addTooth(UUID id,ClinicalModels.ToothRequest request,UUID actor,String correlationId){
        requireDraftOwner(id,actor);
        if(!validFdi(request.toothNumber()))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FDI_TOOTH","Số răng không thuộc hệ FDI.");
        UUID toothId=UUID.randomUUID();
        jdbc.update("INSERT INTO dental_record_teeth(id,record_id,tooth_number,condition_code,surfaces,notes) VALUES (?,?,?,?,?,?)",
                toothId,id,request.toothNumber(),request.conditionCode().trim(),request.surfaces()==null?new String[0]:request.surfaces().toArray(String[]::new),blank(request.notes()));
        audit(actor,"DOCTOR","CLINICAL_TOOTH_RECORDED",toothId,correlationId);
        return new ClinicalModels.ToothResponse(toothId,request.toothNumber(),request.conditionCode(),request.surfaces()==null?List.of():request.surfaces(),request.notes());
    }

    @Transactional
    public ClinicalModels.ProcedureResponse addProcedure(UUID id,ClinicalModels.ProcedureRequest request,SchedulingLookupClient.ServiceItem service,
                                                          UUID actor,String correlationId){
        requireDraftOwner(id,actor);
        List<Integer> teeth=request.toothNumbers()==null?List.of():request.toothNumbers();
        if(teeth.stream().anyMatch(t->!validFdi(t)))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FDI_TOOTH","Số răng không thuộc hệ FDI.");
        var parent=jdbc.queryForMap("SELECT patient_id FROM clinical_records WHERE id=?",id);
        var quantity=request.quantity()==null?java.math.BigDecimal.ONE:request.quantity();
        var discount=request.discountAmount()==null?java.math.BigDecimal.ZERO:request.discountAmount();
        if(discount.compareTo(service.price().multiply(quantity))>0)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DISCOUNT","Mức giảm vượt quá giá trị thủ thuật.");
        UUID procedureId=UUID.randomUUID();
        jdbc.update("INSERT INTO performed_procedures(id,patient_id,record_id,doctor_id,service_id,title_snapshot,tooth_numbers,quantity,unit_price,discount_amount,notes,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                procedureId,parent.get("patient_id"),id,actor,service.id(),service.name(),teeth.toArray(Integer[]::new),quantity,service.price(),discount,blank(request.notes()),actor);
        audit(actor,"DOCTOR","PROCEDURE_RECORDED",procedureId,correlationId);
        return jdbc.queryForObject("SELECT id,service_id,title_snapshot,tooth_numbers,quantity,unit_price,discount_amount,net_amount,performed_at FROM performed_procedures WHERE id=?",
                (rs,n)->new ClinicalModels.ProcedureResponse(rs.getObject("id",UUID.class),rs.getObject("service_id",UUID.class),rs.getString("title_snapshot"),Arrays.asList((Integer[])rs.getArray("tooth_numbers").getArray()),rs.getBigDecimal("quantity"),rs.getBigDecimal("unit_price"),rs.getBigDecimal("discount_amount"),rs.getBigDecimal("net_amount"),rs.getTimestamp("performed_at").toInstant()),procedureId);
    }

    @Transactional
    public ClinicalModels.RecordResponse sign(UUID id,UUID actor,String correlationId,long expectedVersion){
        int count=jdbc.update("UPDATE clinical_records SET status='SIGNED',signed_at=now(),signed_by=?,version=version+1,updated_at=now() WHERE id=? AND doctor_id=? AND status='DRAFT' AND version=?",actor,id,actor,expectedVersion);
        if(count==0){if(!exists(id))throw notFound();if(!isOwner(id,actor))throw forbidden();throw new ApiException(HttpStatus.CONFLICT,"RECORD_NOT_SIGNABLE","Bệnh án đã chốt hoặc phiên bản đã thay đổi.");}
        var record=find(id);
        audit(actor,"DOCTOR","CLINICAL_RECORD_SIGNED",id,correlationId);
        outbox(id,record.version(),correlationId,Map.of("clinicalRecordId",id,"appointmentId",record.appointmentId(),"patientId",record.patientId(),"doctorId",actor,"signedAt",record.signedAt()));
        return record;
    }

    @Transactional
    public ClinicalModels.PrescriptionResponse issuePrescription(UUID recordId,ClinicalModels.IssuePrescriptionRequest request,UUID actor,String correlationId){
        var record=jdbc.query("SELECT patient_id,doctor_id,status,diagnosis FROM clinical_records WHERE id=? FOR SHARE",
                (rs,n)->new RxParent(rs.getObject("patient_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getString("status"),rs.getString("diagnosis")),recordId).stream().findFirst().orElseThrow(this::notFound);
        if(!record.doctorId().equals(actor))throw forbidden();
        if(!"SIGNED".equals(record.status()))throw new ApiException(HttpStatus.CONFLICT,"RECORD_MUST_BE_SIGNED","Chỉ bệnh án đã chốt mới phát hành được đơn thuốc.");
        UUID rxId=UUID.randomUUID();
        jdbc.update("INSERT INTO prescriptions(id,prescription_number,patient_id,record_id,doctor_id,diagnosis_snapshot,notes,status) VALUES (?,?,?,?,?,?,?,'DRAFT')",
                rxId,"RX-"+rxId.toString().substring(0,8).toUpperCase(),record.patientId(),recordId,actor,record.diagnosis(),blank(request.notes()));
        for(var item:request.items())jdbc.update("INSERT INTO prescription_items(prescription_id,medication_name,dosage,route,frequency,duration_days,quantity,instructions) VALUES (?,?,?,?,?,?,?,?)",
                rxId,item.medicationName().trim(),item.dosage().trim(),item.route().trim(),item.frequency().trim(),item.durationDays(),item.quantity().trim(),blank(item.instructions()));
        jdbc.update("UPDATE prescriptions SET status='ISSUED',issued_at=now() WHERE id=? AND status='DRAFT'",rxId);
        audit(actor,"DOCTOR","PRESCRIPTION_ISSUED",rxId,correlationId);
        return new ClinicalModels.PrescriptionResponse(rxId,"RX-"+rxId.toString().substring(0,8).toUpperCase(),Instant.now(),"ISSUED",request.items().size());
    }

    @Transactional(readOnly=true)
    public ClinicalModels.RecordResponse get(UUID id,UUID actor,Set<String> roles){
        ClinicalModels.RecordResponse record=find(id);
        if(roles.contains("PATIENT")) throw forbidden();
        if(roles.contains("DOCTOR")&&!record.doctorId().equals(actor))throw forbidden();
        if(!roles.contains("DOCTOR"))throw forbidden();
        return record;
    }
    @Transactional(readOnly=true)
    public List<ClinicalModels.RecordResponse> history(UUID patientId,UUID doctorId,boolean signedOnly){
        return jdbc.query("SELECT * FROM clinical_records WHERE patient_id=? AND (?::uuid IS NULL OR doctor_id=?) AND (?=false OR status='SIGNED') ORDER BY visit_at DESC LIMIT 200",
                (rs,n)->map(rs),patientId,doctorId,doctorId,signedOnly);
    }

    @Transactional(readOnly=true)
    public ClinicalModels.RecordWorkspace workspaceByAppointment(UUID appointmentId,UUID actor){
        UUID id=jdbc.query("SELECT id FROM clinical_records WHERE appointment_id=? AND amends_record_id IS NULL AND doctor_id=?",
                (rs,n)->rs.getObject("id",UUID.class),appointmentId,actor).stream().findFirst().orElseThrow(this::notFound);
        return workspace(id,actor,Set.of("DOCTOR"));
    }

    @Transactional(readOnly=true)
    public ClinicalModels.RecordWorkspace workspace(UUID id,UUID actor,Set<String> roles){
        var record=get(id,actor,roles);
        return new ClinicalModels.RecordWorkspace(record,teeth(id),procedures(id),prescriptions(id));
    }

    @Transactional(readOnly=true)
    public List<ClinicalModels.PublishedRecord> publishedHistory(UUID patientId){
        var ids=jdbc.query("SELECT id FROM clinical_records WHERE patient_id=? AND status='SIGNED' ORDER BY visit_at DESC LIMIT 200",
                (rs,n)->rs.getObject("id",UUID.class),patientId);
        return ids.stream().map(this::publishedRecord).toList();
    }

    @Transactional(readOnly=true)
    public ClinicalModels.PublishedRecord publishedRecord(UUID recordId,UUID patientId){
        var record=jdbc.query("SELECT * FROM clinical_records WHERE id=? AND patient_id=? AND status='SIGNED'",
                (rs,n)->map(rs),recordId,patientId).stream().findFirst().orElseThrow(this::notFound);
        return new ClinicalModels.PublishedRecord(record,teeth(recordId),procedures(recordId),prescriptions(recordId));
    }

    private ClinicalModels.PublishedRecord publishedRecord(UUID id){
        var record=jdbc.query("SELECT * FROM clinical_records WHERE id=? AND status='SIGNED'",
                (rs,n)->map(rs),id).stream().findFirst().orElseThrow(this::notFound);
        return new ClinicalModels.PublishedRecord(record,teeth(id),procedures(id),prescriptions(id));
    }

    private List<ClinicalModels.ToothResponse> teeth(UUID recordId){
        return jdbc.query("SELECT id,tooth_number,condition_code,surfaces,notes FROM dental_record_teeth WHERE record_id=? ORDER BY tooth_number",
                (rs,n)->new ClinicalModels.ToothResponse(rs.getObject("id",UUID.class),rs.getInt("tooth_number"),rs.getString("condition_code"),
                        Arrays.asList((String[])rs.getArray("surfaces").getArray()),rs.getString("notes")),recordId);
    }

    private List<ClinicalModels.ProcedureResponse> procedures(UUID recordId){
        return jdbc.query("SELECT id,service_id,title_snapshot,tooth_numbers,quantity,unit_price,discount_amount,net_amount,performed_at FROM performed_procedures WHERE record_id=? ORDER BY performed_at,id",
                (rs,n)->procedure(rs),recordId);
    }

    private List<ClinicalModels.PrescriptionDetail> prescriptions(UUID recordId){
        var headers=jdbc.query("SELECT id,prescription_number,issued_at,status,notes FROM prescriptions WHERE record_id=? AND status='ISSUED' ORDER BY issued_at DESC",
                (rs,n)->new PrescriptionHeader(rs.getObject("id",UUID.class),rs.getString("prescription_number"),rs.getTimestamp("issued_at").toInstant(),rs.getString("status"),rs.getString("notes")),recordId);
        return headers.stream().map(header->new ClinicalModels.PrescriptionDetail(header.id(),header.number(),header.issuedAt(),header.status(),header.notes(),
                jdbc.query("SELECT id,medication_name,dosage,route,frequency,duration_days,quantity,instructions FROM prescription_items WHERE prescription_id=? ORDER BY id",
                        (rs,n)->new ClinicalModels.PrescriptionItemResponse(rs.getObject("id",UUID.class),rs.getString("medication_name"),rs.getString("dosage"),rs.getString("route"),rs.getString("frequency"),rs.getInt("duration_days"),rs.getString("quantity"),rs.getString("instructions")),header.id()))).toList();
    }

    private ClinicalModels.ProcedureResponse procedure(java.sql.ResultSet rs)throws java.sql.SQLException{
        return new ClinicalModels.ProcedureResponse(rs.getObject("id",UUID.class),rs.getObject("service_id",UUID.class),rs.getString("title_snapshot"),
                Arrays.asList((Integer[])rs.getArray("tooth_numbers").getArray()),rs.getBigDecimal("quantity"),rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("discount_amount"),rs.getBigDecimal("net_amount"),rs.getTimestamp("performed_at").toInstant());
    }

    private ClinicalModels.RecordResponse find(UUID id){return jdbc.query("SELECT * FROM clinical_records WHERE id=?",(rs,n)->map(rs),id).stream().findFirst().orElseThrow(this::notFound);}
    private ClinicalModels.RecordResponse map(java.sql.ResultSet rs)throws java.sql.SQLException{return new ClinicalModels.RecordResponse(rs.getObject("id",UUID.class),rs.getString("record_number"),rs.getObject("patient_id",UUID.class),rs.getObject("appointment_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getTimestamp("visit_at").toInstant(),rs.getString("chief_complaint"),rs.getString("medical_history"),rs.getString("examination_notes"),rs.getString("diagnosis"),rs.getString("treatment_notes"),rs.getTimestamp("follow_up_at")==null?null:rs.getTimestamp("follow_up_at").toInstant(),rs.getString("status"),rs.getObject("amends_record_id",UUID.class),rs.getString("amendment_reason"),rs.getTimestamp("signed_at")==null?null:rs.getTimestamp("signed_at").toInstant(),rs.getLong("version"));}
    private void requireDraftOwner(UUID id,UUID actor){var row=jdbc.query("SELECT doctor_id,status FROM clinical_records WHERE id=? FOR UPDATE",(rs,n)->new Owner(rs.getObject("doctor_id",UUID.class),rs.getString("status")),id).stream().findFirst().orElseThrow(this::notFound);if(!row.doctorId().equals(actor))throw forbidden();if(!"DRAFT".equals(row.status()))throw new ApiException(HttpStatus.CONFLICT,"RECORD_IMMUTABLE","Bệnh án đã chốt và không thể sửa.");}
    private boolean exists(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM clinical_records WHERE id=?)",Boolean.class,id));}
    private boolean isOwner(UUID id,UUID actor){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM clinical_records WHERE id=? AND doctor_id=?)",Boolean.class,id,actor));}
    private void audit(UUID actor,String label,String action,UUID id,String trace){jdbc.update("INSERT INTO audit_events(actor_user_id,actor_label,action,entity_type,entity_id,request_id,metadata) VALUES (?,?,?,?,?,?,?::jsonb)",actor,label,action,"ClinicalRecord",id,trace,"{}");}
    private void outbox(UUID id,long version,String correlation,Map<String,Object> payload){try{jdbc.update("INSERT INTO outbox_events(event_id,event_type,schema_version,occurred_at,aggregate_id,aggregate_version,correlation_id,payload) VALUES (?,?,1,?,?,?,?,?::jsonb)",UUID.randomUUID(),"ClinicalRecordSigned.v1",databaseTime(Instant.now()),id,version,correlation,json.writeValueAsString(payload));}catch(JacksonException e){throw new IllegalStateException(e);}}
    private static java.time.OffsetDateTime databaseTime(Instant instant){return instant.atOffset(java.time.ZoneOffset.UTC);}
    private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"CLINICAL_RECORD_NOT_FOUND","Không tìm thấy bệnh án.");}
    private ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Bạn không có quyền truy cập hoặc sửa bệnh án này.");}
    private static boolean validFdi(int n){return (n>=11&&n<=18)||(n>=21&&n<=28)||(n>=31&&n<=38)||(n>=41&&n<=48)||(n>=51&&n<=55)||(n>=61&&n<=65)||(n>=71&&n<=75)||(n>=81&&n<=85);}
    private static String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private record Original(UUID id,UUID patientId,UUID appointmentId,UUID doctorId,Instant visitAt,String status){}
    private record RxParent(UUID patientId,UUID doctorId,String status,String diagnosis){}
    private record Owner(UUID doctorId,String status){}
    private record PrescriptionHeader(UUID id,String number,Instant issuedAt,String status,String notes){}
}

