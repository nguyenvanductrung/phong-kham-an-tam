package vn.phongkham.clinical.persistence;

import java.time.*;
import java.math.BigDecimal;
import vn.phongkham.clinical.api.ClinicalModels;
import java.util.*;

/** Persistence contract; SQL is implemented by JdbcClinicalRecordDao. */
public interface ClinicalRecordDao {
    int insertRecord(UUID id, String number, UUID patient, UUID appointment, UUID doctor, OffsetDateTime visitAt, String complaint);
    UUID findAppointmentRecord(UUID appointment);
    int updateDraft(String complaint, String medicalHistory, String examination, String diagnosis, String treatment, OffsetDateTime followUp, UUID id, UUID doctor, long version);
    List<Original> findOriginal(UUID id);
    int insertAmendment(UUID id, String number, UUID patient, UUID appointment, UUID doctor, OffsetDateTime visitAt, UUID original, String reason);
    int insertTooth(UUID id, UUID record, int tooth, String condition, String[] surfaces, String notes);
    Map<String, Object> findProcedureParent(UUID id);
    int insertProcedure(UUID id, UUID patient, UUID record, UUID doctor, UUID service, String title, Integer[] teeth, BigDecimal quantity, BigDecimal price, BigDecimal discount, String notes, UUID actor);
    ClinicalModels.ProcedureResponse findProcedure(UUID id);
    int signRecord(UUID signer, UUID id, UUID doctor, long version);
    List<RxParent> findPrescriptionParent(UUID record);
    int insertPrescription(UUID id, String number, UUID patient, UUID record, UUID doctor, String diagnosis, String notes);
    int insertPrescriptionItem(UUID prescription, String medication, String dosage, String route, String frequency, int days, String quantity, String instructions);
    int issuePrescription(UUID id);
    List<ClinicalModels.RecordResponse> listHistory(UUID patient, UUID doctorFilter, UUID doctor, boolean signedOnly);
    List<UUID> findOwnedAppointmentRecord(UUID appointment, UUID doctor);
    List<UUID> listPublishedIds(UUID patient);
    List<ClinicalModels.RecordResponse> findPublishedRecord(UUID id, UUID patient);
    List<ClinicalModels.RecordResponse> findSignedRecord(UUID id);
    List<ClinicalModels.ToothResponse> listTeeth(UUID record);
    List<ClinicalModels.ProcedureResponse> listProcedures(UUID record);
    List<PrescriptionHeader> listPrescriptions(UUID record);
    List<ClinicalModels.PrescriptionItemResponse> listPrescriptionItems(UUID prescription);
    List<ClinicalModels.RecordResponse> findRecord(UUID id);
    List<Owner> findLockedOwner(UUID record);
    Boolean exists(UUID id);
    Boolean isOwner(UUID id, UUID doctor);
    int insertAudit(UUID actor, String label, String action, String entityType, UUID entity, String trace, String metadata);
    int insertOutbox(UUID eventId, String type, OffsetDateTime occurredAt, UUID aggregate, long version, String trace, String payload);
    record Original(UUID id,UUID patientId,UUID appointmentId,UUID doctorId,Instant visitAt,String status){}
    record RxParent(UUID patientId,UUID doctorId,String status,String diagnosis){}
    record Owner(UUID doctorId,String status){}
    record PrescriptionHeader(UUID id,String number,Instant issuedAt,String status,String notes){}
}
