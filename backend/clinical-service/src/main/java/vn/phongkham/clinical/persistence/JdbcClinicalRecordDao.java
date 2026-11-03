package vn.phongkham.clinical.persistence;

import java.time.*;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.phongkham.clinical.api.ClinicalModels;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.phongkham.clinical.persistence.ClinicalRecordDao.*;

@Repository
public class JdbcClinicalRecordDao implements ClinicalRecordDao {
    private final JdbcTemplate jdbc;
    public JdbcClinicalRecordDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public int insertRecord(UUID id, String number, UUID patient, UUID appointment, UUID doctor, OffsetDateTime visitAt, String complaint) {
        return jdbc.update("INSERT INTO clinical_records(id,record_number,patient_id,appointment_id,doctor_id,visit_at,chief_complaint) VALUES (?,?,?,?,?,?,?) ON CONFLICT (appointment_id) WHERE amends_record_id IS NULL DO NOTHING", new Object[]{id, number, patient, appointment, doctor, visitAt, complaint});
    }

    @Override
    public UUID findAppointmentRecord(UUID appointment) {
        return jdbc.queryForObject("SELECT id FROM clinical_records WHERE appointment_id=? AND amends_record_id IS NULL", UUID.class, new Object[]{appointment});
    }

    @Override
    public int updateDraft(String complaint, String medicalHistory, String examination, String diagnosis, String treatment, OffsetDateTime followUp, UUID id, UUID doctor, long version) {
        return jdbc.update("""
                UPDATE clinical_records SET chief_complaint=?,medical_history=?,examination_notes=?,diagnosis=?,treatment_notes=?,follow_up_at=?,version=version+1,updated_at=now()
                WHERE id=? AND doctor_id=? AND status='DRAFT' AND version=?
                """, new Object[]{complaint, medicalHistory, examination, diagnosis, treatment, followUp, id, doctor, version});
    }

    @Override
    public List<Original> findOriginal(UUID id) {
        return jdbc.query("SELECT id,patient_id,appointment_id,doctor_id,visit_at,status FROM clinical_records WHERE id=? FOR UPDATE", (rs,n)->new Original(rs.getObject("id",UUID.class),rs.getObject("patient_id",UUID.class),rs.getObject("appointment_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getTimestamp("visit_at").toInstant(),rs.getString("status")), new Object[]{id});
    }

    @Override
    public int insertAmendment(UUID id, String number, UUID patient, UUID appointment, UUID doctor, OffsetDateTime visitAt, UUID original, String reason) {
        return jdbc.update("INSERT INTO clinical_records(id,record_number,patient_id,appointment_id,doctor_id,visit_at,amends_record_id,amendment_reason) VALUES (?,?,?,?,?,?,?,?)", new Object[]{id, number, patient, appointment, doctor, visitAt, original, reason});
    }

    @Override
    public int insertTooth(UUID id, UUID record, int tooth, String condition, String[] surfaces, String notes) {
        return jdbc.update("INSERT INTO dental_record_teeth(id,record_id,tooth_number,condition_code,surfaces,notes) VALUES (?,?,?,?,?,?)", new Object[]{id, record, tooth, condition, surfaces, notes});
    }

    @Override
    public Map<String, Object> findProcedureParent(UUID id) {
        return jdbc.queryForMap("SELECT patient_id FROM clinical_records WHERE id=?", new Object[]{id});
    }

    @Override
    public int insertProcedure(UUID id, UUID patient, UUID record, UUID doctor, UUID service, String title, Integer[] teeth, BigDecimal quantity, BigDecimal price, BigDecimal discount, String notes, UUID actor) {
        return jdbc.update("INSERT INTO performed_procedures(id,patient_id,record_id,doctor_id,service_id,title_snapshot,tooth_numbers,quantity,unit_price,discount_amount,notes,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", new Object[]{id, patient, record, doctor, service, title, teeth, quantity, price, discount, notes, actor});
    }

    @Override
    public ClinicalModels.ProcedureResponse findProcedure(UUID id) {
        return jdbc.queryForObject("SELECT id,service_id,title_snapshot,tooth_numbers,quantity,unit_price,discount_amount,net_amount,performed_at FROM performed_procedures WHERE id=?", (rs,n)->new ClinicalModels.ProcedureResponse(rs.getObject("id",UUID.class),rs.getObject("service_id",UUID.class),rs.getString("title_snapshot"),Arrays.asList((Integer[])rs.getArray("tooth_numbers").getArray()),rs.getBigDecimal("quantity"),rs.getBigDecimal("unit_price"),rs.getBigDecimal("discount_amount"),rs.getBigDecimal("net_amount"),rs.getTimestamp("performed_at").toInstant()), new Object[]{id});
    }

    @Override
    public int signRecord(UUID signer, UUID id, UUID doctor, long version) {
        return jdbc.update("UPDATE clinical_records SET status='SIGNED',signed_at=now(),signed_by=?,version=version+1,updated_at=now() WHERE id=? AND doctor_id=? AND status='DRAFT' AND version=?", new Object[]{signer, id, doctor, version});
    }

    @Override
    public List<RxParent> findPrescriptionParent(UUID record) {
        return jdbc.query("SELECT patient_id,doctor_id,status,diagnosis FROM clinical_records WHERE id=? FOR SHARE", (rs,n)->new RxParent(rs.getObject("patient_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getString("status"),rs.getString("diagnosis")), new Object[]{record});
    }

    @Override
    public int insertPrescription(UUID id, String number, UUID patient, UUID record, UUID doctor, String diagnosis, String notes) {
        return jdbc.update("INSERT INTO prescriptions(id,prescription_number,patient_id,record_id,doctor_id,diagnosis_snapshot,notes,status) VALUES (?,?,?,?,?,?,?,'DRAFT')", new Object[]{id, number, patient, record, doctor, diagnosis, notes});
    }

    @Override
    public int insertPrescriptionItem(UUID prescription, String medication, String dosage, String route, String frequency, int days, String quantity, String instructions) {
        return jdbc.update("INSERT INTO prescription_items(prescription_id,medication_name,dosage,route,frequency,duration_days,quantity,instructions) VALUES (?,?,?,?,?,?,?,?)", new Object[]{prescription, medication, dosage, route, frequency, days, quantity, instructions});
    }

    @Override
    public int issuePrescription(UUID id) {
        return jdbc.update("UPDATE prescriptions SET status='ISSUED',issued_at=now() WHERE id=? AND status='DRAFT'", new Object[]{id});
    }

    @Override
    public List<ClinicalModels.RecordResponse> listHistory(UUID patient, UUID doctorFilter, UUID doctor, boolean signedOnly) {
        return jdbc.query("SELECT * FROM clinical_records WHERE patient_id=? AND (?::uuid IS NULL OR doctor_id=?) AND (?=false OR status='SIGNED') ORDER BY visit_at DESC LIMIT 200", (rs,n)->map(rs), new Object[]{patient, doctorFilter, doctor, signedOnly});
    }

    @Override
    public List<UUID> findOwnedAppointmentRecord(UUID appointment, UUID doctor) {
        return jdbc.query("SELECT id FROM clinical_records WHERE appointment_id=? AND amends_record_id IS NULL AND doctor_id=?", (rs,n)->rs.getObject("id",UUID.class), new Object[]{appointment, doctor});
    }

    @Override
    public List<UUID> listPublishedIds(UUID patient) {
        return jdbc.query("SELECT id FROM clinical_records WHERE patient_id=? AND status='SIGNED' ORDER BY visit_at DESC LIMIT 200", (rs,n)->rs.getObject("id",UUID.class), new Object[]{patient});
    }

    @Override
    public List<ClinicalModels.RecordResponse> findPublishedRecord(UUID id, UUID patient) {
        return jdbc.query("SELECT * FROM clinical_records WHERE id=? AND patient_id=? AND status='SIGNED'", (rs,n)->map(rs), new Object[]{id, patient});
    }

    @Override
    public List<ClinicalModels.RecordResponse> findSignedRecord(UUID id) {
        return jdbc.query("SELECT * FROM clinical_records WHERE id=? AND status='SIGNED'", (rs,n)->map(rs), new Object[]{id});
    }

    @Override
    public List<ClinicalModels.ToothResponse> listTeeth(UUID record) {
        return jdbc.query("SELECT id,tooth_number,condition_code,surfaces,notes FROM dental_record_teeth WHERE record_id=? ORDER BY tooth_number", (rs,n)->new ClinicalModels.ToothResponse(rs.getObject("id",UUID.class),rs.getInt("tooth_number"),rs.getString("condition_code"),
                        Arrays.asList((String[])rs.getArray("surfaces").getArray()),rs.getString("notes")), new Object[]{record});
    }

    @Override
    public List<ClinicalModels.ProcedureResponse> listProcedures(UUID record) {
        return jdbc.query("SELECT id,service_id,title_snapshot,tooth_numbers,quantity,unit_price,discount_amount,net_amount,performed_at FROM performed_procedures WHERE record_id=? ORDER BY performed_at,id", (rs,n)->procedure(rs), new Object[]{record});
    }

    @Override
    public List<PrescriptionHeader> listPrescriptions(UUID record) {
        return jdbc.query("SELECT id,prescription_number,issued_at,status,notes FROM prescriptions WHERE record_id=? AND status='ISSUED' ORDER BY issued_at DESC", (rs,n)->new PrescriptionHeader(rs.getObject("id",UUID.class),rs.getString("prescription_number"),rs.getTimestamp("issued_at").toInstant(),rs.getString("status"),rs.getString("notes")), new Object[]{record});
    }

    @Override
    public List<ClinicalModels.PrescriptionItemResponse> listPrescriptionItems(UUID prescription) {
        return jdbc.query("SELECT id,medication_name,dosage,route,frequency,duration_days,quantity,instructions FROM prescription_items WHERE prescription_id=? ORDER BY id", (rs,n)->new ClinicalModels.PrescriptionItemResponse(rs.getObject("id",UUID.class),rs.getString("medication_name"),rs.getString("dosage"),rs.getString("route"),rs.getString("frequency"),rs.getInt("duration_days"),rs.getString("quantity"),rs.getString("instructions")), new Object[]{prescription});
    }

    @Override
    public List<ClinicalModels.RecordResponse> findRecord(UUID id) {
        return jdbc.query("SELECT * FROM clinical_records WHERE id=?", (rs,n)->map(rs), new Object[]{id});
    }

    @Override
    public List<Owner> findLockedOwner(UUID record) {
        return jdbc.query("SELECT doctor_id,status FROM clinical_records WHERE id=? FOR UPDATE", (rs,n)->new Owner(rs.getObject("doctor_id",UUID.class),rs.getString("status")), new Object[]{record});
    }

    @Override
    public Boolean exists(UUID id) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM clinical_records WHERE id=?)", Boolean.class, new Object[]{id});
    }

    @Override
    public Boolean isOwner(UUID id, UUID doctor) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM clinical_records WHERE id=? AND doctor_id=?)", Boolean.class, new Object[]{id, doctor});
    }

    @Override
    public int insertAudit(UUID actor, String label, String action, String entityType, UUID entity, String trace, String metadata) {
        return jdbc.update("INSERT INTO audit_events(actor_user_id,actor_label,action,entity_type,entity_id,request_id,metadata) VALUES (?,?,?,?,?,?,?::jsonb)", new Object[]{actor, label, action, entityType, entity, trace, metadata});
    }

    @Override
    public int insertOutbox(UUID eventId, String type, OffsetDateTime occurredAt, UUID aggregate, long version, String trace, String payload) {
        return jdbc.update("INSERT INTO outbox_events(event_id,event_type,schema_version,occurred_at,aggregate_id,aggregate_version,correlation_id,payload) VALUES (?,?,1,?,?,?,?,?::jsonb)", new Object[]{eventId, type, occurredAt, aggregate, version, trace, payload});
    }

    private ClinicalModels.RecordResponse map(java.sql.ResultSet rs)throws java.sql.SQLException{return new ClinicalModels.RecordResponse(rs.getObject("id",UUID.class),rs.getString("record_number"),rs.getObject("patient_id",UUID.class),rs.getObject("appointment_id",UUID.class),rs.getObject("doctor_id",UUID.class),rs.getTimestamp("visit_at").toInstant(),rs.getString("chief_complaint"),rs.getString("medical_history"),rs.getString("examination_notes"),rs.getString("diagnosis"),rs.getString("treatment_notes"),rs.getTimestamp("follow_up_at")==null?null:rs.getTimestamp("follow_up_at").toInstant(),rs.getString("status"),rs.getObject("amends_record_id",UUID.class),rs.getString("amendment_reason"),rs.getTimestamp("signed_at")==null?null:rs.getTimestamp("signed_at").toInstant(),rs.getLong("version"));}

    private ClinicalModels.ProcedureResponse procedure(java.sql.ResultSet rs)throws java.sql.SQLException{
        return new ClinicalModels.ProcedureResponse(rs.getObject("id",UUID.class),rs.getObject("service_id",UUID.class),rs.getString("title_snapshot"),
                Arrays.asList((Integer[])rs.getArray("tooth_numbers").getArray()),rs.getBigDecimal("quantity"),rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("discount_amount"),rs.getBigDecimal("net_amount"),rs.getTimestamp("performed_at").toInstant());
    }
}
