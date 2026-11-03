package vn.phongkham.patient.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.phongkham.patient.api.PatientModels;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPatientDao implements PatientDao {
    private final JdbcTemplate jdbc;
    private static final RowMapper<PatientModels.PatientSummary> SUMMARY = (rs,n) -> new PatientModels.PatientSummary(
            rs.getObject("id",UUID.class),rs.getString("patient_code"),rs.getString("full_name"),rs.getString("phone"),rs.getString("email"),
            rs.getObject("date_of_birth",java.time.LocalDate.class),rs.getString("gender"),rs.getString("address"),
            rs.getString("emergency_contact"),rs.getString("emergency_phone"),rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("deleted_at")==null?null:rs.getTimestamp("deleted_at").toInstant());
    public JdbcPatientDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Override public List<PatientModels.PatientSummary> search(String query,String status,int limit,long offset) {
        return jdbc.query("""
                SELECT * FROM patients
                WHERE (?='ALL' OR (?='ACTIVE' AND deleted_at IS NULL) OR (?='ARCHIVED' AND deleted_at IS NOT NULL))
                  AND (?='' OR full_name ILIKE ? OR phone LIKE ? OR patient_code ILIKE ?)
                ORDER BY full_name,id LIMIT ? OFFSET ?
                """,SUMMARY,status,status,status,query,"%"+query+"%","%"+query+"%","%"+query+"%",limit,offset);
    }
    @Override public Optional<PatientModels.PatientSummary> findSummary(UUID id) {
        return jdbc.query("SELECT * FROM patients WHERE id=?",SUMMARY,id).stream().findFirst();
    }
    @Override public Optional<PatientModels.PatientSummary> lock(UUID id) {
        return jdbc.query("SELECT * FROM patients WHERE id=? FOR UPDATE",SUMMARY,id).stream().findFirst();
    }
    @Override public Optional<PatientModels.PatientDetail> findDetail(UUID id) {
        return jdbc.query("SELECT * FROM patients WHERE id=?",(rs,n)->new PatientModels.PatientDetail(
                rs.getObject("id",UUID.class),rs.getString("patient_code"),rs.getString("full_name"),rs.getString("phone"),rs.getString("email"),
                rs.getObject("date_of_birth",java.time.LocalDate.class),rs.getString("gender"),rs.getString("address"),rs.getString("emergency_contact"),
                rs.getString("emergency_phone"),rs.getString("allergies"),rs.getString("medical_history"),rs.getString("notes"),
                rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("deleted_at")==null?null:rs.getTimestamp("deleted_at").toInstant()),id).stream().findFirst();
    }
    @Override public void insert(UUID id,String code,PatientModels.CreatePatientRequest r,UUID actor) {
        jdbc.update("""
                INSERT INTO patients(id,patient_code,full_name,phone,email,gender,date_of_birth,address,emergency_contact,emergency_phone,allergies,medical_history,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,id,code,r.fullName().trim(),r.phone().trim(),blank(r.email()),blank(r.gender()),r.dateOfBirth(),blank(r.address()),
                blank(r.emergencyContact()),blank(r.emergencyPhone()),blank(r.allergies()),blank(r.medicalHistory()),actor);
    }
    @Override public int update(UUID id,PatientModels.UpdatePatientRequest r) {
        // Administrative updates preserve clinical allergy/history fields.
        return jdbc.update("""
                UPDATE patients SET full_name=?,phone=?,email=?,gender=?,date_of_birth=?,address=?,emergency_contact=?,emergency_phone=?,updated_at=now()
                WHERE id=? AND deleted_at IS NULL
                """,r.fullName().trim(),r.phone().trim(),blank(r.email()),blank(r.gender()),r.dateOfBirth(),blank(r.address()),blank(r.emergencyContact()),blank(r.emergencyPhone()),id);
    }
    @Override public int delete(UUID id) { return jdbc.update("DELETE FROM patients WHERE id=?",id); }
    @Override public void setArchived(UUID id,boolean archived) {
        jdbc.update("UPDATE patients SET deleted_at=CASE WHEN ? THEN COALESCE(deleted_at,now()) ELSE NULL END,updated_at=now() WHERE id=?",archived,id);
    }
    @Override public boolean hasLocalReferences(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM patient_account_links WHERE patient_id=?)
                    OR EXISTS(SELECT 1 FROM patient_consents WHERE patient_id=?)
                    OR EXISTS(SELECT 1 FROM patient_access_grants WHERE patient_id=?)
                """,Boolean.class,id,id,id));
    }
    @Override public boolean isLinkedTo(UUID patientId,UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM patient_account_links WHERE patient_id=? AND user_id=?)",Boolean.class,patientId,userId));
    }
    @Override public Optional<UUID> findLinkedPatient(UUID userId) {
        return jdbc.query("SELECT patient_id FROM patient_account_links WHERE user_id=?",(rs,n)->rs.getObject(1,UUID.class),userId).stream().findFirst();
    }
    @Override public void linkAccount(UUID patientId,UUID userId,UUID actor) {
        jdbc.update("INSERT INTO patient_account_links(patient_id,user_id,linked_by) VALUES (?,?,?)",patientId,userId,actor);
    }
    @Override public Optional<UUID> findLinkedAccount(UUID patientId) {
        return jdbc.query("SELECT user_id FROM patient_account_links WHERE patient_id=?",(rs,n)->rs.getObject(1,UUID.class),patientId).stream().findFirst();
    }
    @Override public boolean hasDoctorGrant(UUID patientId,UUID doctorId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM patient_access_grants WHERE patient_id=? AND doctor_id=? AND expires_at>now() AND revoked_at IS NULL)",Boolean.class,patientId,doctorId));
    }
    @Override public void audit(UUID actor,String action,UUID patientId,String trace) {
        jdbc.update("INSERT INTO patient_audit_events(actor_user_id,actor_label,action,patient_id,request_id,metadata) VALUES (?,?,?,?,?,?::jsonb)",actor,"USER:"+actor,action,patientId,trace,"{}");
    }
    private static String blank(String value) { return value==null||value.isBlank()?null:value.trim(); }
}
