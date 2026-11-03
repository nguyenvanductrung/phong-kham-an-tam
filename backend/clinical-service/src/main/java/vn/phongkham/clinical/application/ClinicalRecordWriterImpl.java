package vn.phongkham.clinical.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.HttpStatus;
import vn.phongkham.clinical.persistence.ClinicalRecordDao;
import vn.phongkham.clinical.persistence.ClinicalRecordDao.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.clinical.api.ClinicalModels;
import vn.phongkham.common.ApiException;
import java.time.Instant;
import java.util.*;

@Service
public class ClinicalRecordWriterImpl implements ClinicalRecordWriter {
    private final ClinicalRecordDao dao;
    private final JsonMapper json;
    public ClinicalRecordWriterImpl(ClinicalRecordDao dao,JsonMapper json){this.dao=dao;this.json=json;}

    @Transactional
    public ClinicalModels.RecordResponse createFromAppointment(SchedulingLookupClient.Appointment appointment,UUID actor,String correlationId,String complaint){
        if(!"IN_PROGRESS".equals(appointment.status()))throw new ApiException(HttpStatus.CONFLICT,"APPOINTMENT_NOT_IN_PROGRESS","Chỉ lượt khám đang thực hiện mới tạo được bệnh án.");
        if(!appointment.doctorId().equals(actor))throw new ApiException(HttpStatus.FORBIDDEN,"DOCTOR_NOT_ASSIGNED","Chỉ bác sĩ phụ trách mới được tạo bệnh án.");
        UUID id=UUID.randomUUID();
        int inserted=dao.insertRecord(id, "HS-"+id.toString().substring(0,8).toUpperCase(), appointment.patientId(), appointment.id(), appointment.doctorId(), databaseTime(appointment.scheduledAt()), blank(complaint));
        if(inserted==0){UUID existing=dao.findAppointmentRecord(appointment.id());return find(existing);}
        audit(actor,"DOCTOR","CLINICAL_RECORD_CREATED",id,correlationId);
        return find(id);
    }

    @Transactional
    public ClinicalModels.RecordResponse saveDraft(UUID id,ClinicalModels.SaveDraftRequest request,UUID actor,String correlationId){
        int count=dao.updateDraft(blank(request.chiefComplaint()), blank(request.medicalHistory()), blank(request.examinationNotes()), blank(request.diagnosis()), blank(request.treatmentNotes()), request.followUpAt()==null?null:databaseTime(request.followUpAt()), id, actor, request.version());
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
        var original=dao.findOriginal(originalId).stream().findFirst().orElseThrow(this::notFound);
        if(!original.doctorId().equals(actor))throw forbidden();
        if(!"SIGNED".equals(original.status()))throw new ApiException(HttpStatus.CONFLICT,"ORIGINAL_RECORD_NOT_SIGNED","Chỉ bệnh án đã chốt mới được bổ sung.");
        UUID id=UUID.randomUUID();
        dao.insertAmendment(id, "HS-"+id.toString().substring(0,8).toUpperCase(), original.patientId(), original.appointmentId(), original.doctorId(), databaseTime(Instant.now()), originalId, reason.trim());
        audit(actor,"DOCTOR","CLINICAL_RECORD_AMENDMENT_CREATED",id,correlationId);
        return find(id);
    }

    @Transactional
    public ClinicalModels.ToothResponse addTooth(UUID id,ClinicalModels.ToothRequest request,UUID actor,String correlationId){
        requireDraftOwner(id,actor);
        if(!validFdi(request.toothNumber()))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FDI_TOOTH","Số răng không thuộc hệ FDI.");
        UUID toothId=UUID.randomUUID();
        dao.insertTooth(toothId, id, request.toothNumber(), request.conditionCode().trim(), request.surfaces()==null?new String[0]:request.surfaces().toArray(String[]::new), blank(request.notes()));
        audit(actor,"DOCTOR","CLINICAL_TOOTH_RECORDED",toothId,correlationId);
        return new ClinicalModels.ToothResponse(toothId,request.toothNumber(),request.conditionCode(),request.surfaces()==null?List.of():request.surfaces(),request.notes());
    }

    @Transactional
    public ClinicalModels.ProcedureResponse addProcedure(UUID id,ClinicalModels.ProcedureRequest request,SchedulingLookupClient.ServiceItem service,
                                                          UUID actor,String correlationId){
        requireDraftOwner(id,actor);
        List<Integer> teeth=request.toothNumbers()==null?List.of():request.toothNumbers();
        if(teeth.stream().anyMatch(t->!validFdi(t)))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FDI_TOOTH","Số răng không thuộc hệ FDI.");
        var parent=dao.findProcedureParent(id);
        var quantity=request.quantity()==null?java.math.BigDecimal.ONE:request.quantity();
        var discount=request.discountAmount()==null?java.math.BigDecimal.ZERO:request.discountAmount();
        if(discount.compareTo(service.price().multiply(quantity))>0)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DISCOUNT","Mức giảm vượt quá giá trị thủ thuật.");
        UUID procedureId=UUID.randomUUID();
        dao.insertProcedure(procedureId, (UUID)parent.get("patient_id"), id, actor, service.id(), service.name(), teeth.toArray(Integer[]::new), quantity, service.price(), discount, blank(request.notes()), actor);
        audit(actor,"DOCTOR","PROCEDURE_RECORDED",procedureId,correlationId);
        return dao.findProcedure(procedureId);
    }

    @Transactional
    public ClinicalModels.RecordResponse sign(UUID id,UUID actor,String correlationId,long expectedVersion){
        int count=dao.signRecord(actor, id, actor, expectedVersion);
        if(count==0){if(!exists(id))throw notFound();if(!isOwner(id,actor))throw forbidden();throw new ApiException(HttpStatus.CONFLICT,"RECORD_NOT_SIGNABLE","Bệnh án đã chốt hoặc phiên bản đã thay đổi.");}
        var record=find(id);
        audit(actor,"DOCTOR","CLINICAL_RECORD_SIGNED",id,correlationId);
        outbox(id,record.version(),correlationId,Map.of("clinicalRecordId",id,"appointmentId",record.appointmentId(),"patientId",record.patientId(),"doctorId",actor,"signedAt",record.signedAt()));
        return record;
    }

    @Transactional
    public ClinicalModels.PrescriptionResponse issuePrescription(UUID recordId,ClinicalModels.IssuePrescriptionRequest request,UUID actor,String correlationId){
        var record=dao.findPrescriptionParent(recordId).stream().findFirst().orElseThrow(this::notFound);
        if(!record.doctorId().equals(actor))throw forbidden();
        if(!"SIGNED".equals(record.status()))throw new ApiException(HttpStatus.CONFLICT,"RECORD_MUST_BE_SIGNED","Chỉ bệnh án đã chốt mới phát hành được đơn thuốc.");
        UUID rxId=UUID.randomUUID();
        dao.insertPrescription(rxId, "RX-"+rxId.toString().substring(0,8).toUpperCase(), record.patientId(), recordId, actor, record.diagnosis(), blank(request.notes()));
        for(var item:request.items())dao.insertPrescriptionItem(rxId, item.medicationName().trim(), item.dosage().trim(), item.route().trim(), item.frequency().trim(), item.durationDays(), item.quantity().trim(), blank(item.instructions()));
        dao.issuePrescription(rxId);
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
        return dao.listHistory(patientId, doctorId, doctorId, signedOnly);
    }

    @Transactional(readOnly=true)
    public ClinicalModels.RecordWorkspace workspaceByAppointment(UUID appointmentId,UUID actor){
        UUID id=dao.findOwnedAppointmentRecord(appointmentId, actor).stream().findFirst().orElseThrow(this::notFound);
        return workspace(id,actor,Set.of("DOCTOR"));
    }

    @Transactional(readOnly=true)
    public ClinicalModels.RecordWorkspace workspace(UUID id,UUID actor,Set<String> roles){
        var record=get(id,actor,roles);
        return new ClinicalModels.RecordWorkspace(record,teeth(id),procedures(id),prescriptions(id));
    }

    @Transactional(readOnly=true)
    public List<ClinicalModels.PublishedRecord> publishedHistory(UUID patientId){
        var ids=dao.listPublishedIds(patientId);
        return ids.stream().map(this::publishedRecord).toList();
    }

    @Transactional(readOnly=true)
    public ClinicalModels.PublishedRecord publishedRecord(UUID recordId,UUID patientId){
        var record=dao.findPublishedRecord(recordId, patientId).stream().findFirst().orElseThrow(this::notFound);
        return new ClinicalModels.PublishedRecord(record,teeth(recordId),procedures(recordId),prescriptions(recordId));
    }

    private ClinicalModels.PublishedRecord publishedRecord(UUID id){
        var record=dao.findSignedRecord(id).stream().findFirst().orElseThrow(this::notFound);
        return new ClinicalModels.PublishedRecord(record,teeth(id),procedures(id),prescriptions(id));
    }

    private List<ClinicalModels.ToothResponse> teeth(UUID recordId){
        return dao.listTeeth(recordId);
    }

    private List<ClinicalModels.ProcedureResponse> procedures(UUID recordId){
        return dao.listProcedures(recordId);
    }

    private List<ClinicalModels.PrescriptionDetail> prescriptions(UUID recordId){
        var headers=dao.listPrescriptions(recordId);
        return headers.stream().map(header->new ClinicalModels.PrescriptionDetail(header.id(),header.number(),header.issuedAt(),header.status(),header.notes(),
                dao.listPrescriptionItems(header.id()))).toList();
    }



    private ClinicalModels.RecordResponse find(UUID id){return dao.findRecord(id).stream().findFirst().orElseThrow(this::notFound);}

    private void requireDraftOwner(UUID id,UUID actor){var row=dao.findLockedOwner(id).stream().findFirst().orElseThrow(this::notFound);if(!row.doctorId().equals(actor))throw forbidden();if(!"DRAFT".equals(row.status()))throw new ApiException(HttpStatus.CONFLICT,"RECORD_IMMUTABLE","Bệnh án đã chốt và không thể sửa.");}
    private boolean exists(UUID id){return Boolean.TRUE.equals(dao.exists(id));}
    private boolean isOwner(UUID id,UUID actor){return Boolean.TRUE.equals(dao.isOwner(id, actor));}
    private void audit(UUID actor,String label,String action,UUID id,String trace){dao.insertAudit(actor, label, action, "ClinicalRecord", id, trace, "{}");}
    private void outbox(UUID id,long version,String correlation,Map<String,Object> payload){try{dao.insertOutbox(UUID.randomUUID(), "ClinicalRecordSigned.v1", databaseTime(Instant.now()), id, version, correlation, json.writeValueAsString(payload));}catch(JacksonException e){throw new IllegalStateException(e);}}
    private static java.time.OffsetDateTime databaseTime(Instant instant){return instant.atOffset(java.time.ZoneOffset.UTC);}
    private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"CLINICAL_RECORD_NOT_FOUND","Không tìm thấy bệnh án.");}
    private ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Bạn không có quyền truy cập hoặc sửa bệnh án này.");}
    private static boolean validFdi(int n){return (n>=11&&n<=18)||(n>=21&&n<=28)||(n>=31&&n<=38)||(n>=41&&n<=48)||(n>=51&&n<=55)||(n>=61&&n<=65)||(n>=71&&n<=75)||(n>=81&&n<=85);}
    private static String blank(String value){return value==null||value.isBlank()?null:value.trim();}




}
