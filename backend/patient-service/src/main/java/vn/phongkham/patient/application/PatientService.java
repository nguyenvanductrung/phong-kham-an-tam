package vn.phongkham.patient.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.ApiException;
import vn.phongkham.patient.api.PatientModels;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;

@Service
public class PatientService {
    private final JdbcTemplate jdbc;
    public PatientService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public PatientModels.PatientPage search(String query, int page, int size) {
        int bounded = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        String term = query == null ? "" : query.trim();
        List<PatientModels.PatientSummary> rows = jdbc.query("""
                SELECT id,patient_code,full_name,phone,email,date_of_birth,gender,address,emergency_contact,emergency_phone,created_at
                FROM patients WHERE deleted_at IS NULL AND (?='' OR full_name ILIKE ? OR phone LIKE ? OR patient_code ILIKE ?)
                ORDER BY full_name,id LIMIT ? OFFSET ?
                """, (rs, n) -> new PatientModels.PatientSummary(rs.getObject("id", UUID.class), rs.getString("patient_code"),
                rs.getString("full_name"), rs.getString("phone"), rs.getString("email"), rs.getObject("date_of_birth", java.time.LocalDate.class),
                rs.getString("gender"), rs.getString("address"), rs.getString("emergency_contact"), rs.getString("emergency_phone"),
                rs.getTimestamp("created_at").toInstant()), term, "%" + term + "%", "%" + term + "%", "%" + term + "%", bounded + 1, (long) safePage * bounded);
        boolean hasNext = rows.size() > bounded;
        if (hasNext) rows = rows.subList(0, bounded);
        return new PatientModels.PatientPage(rows, safePage, bounded, hasNext);
    }

    @Transactional
    public PatientModels.PatientSummary create(PatientModels.CreatePatientRequest request, UUID actorId,String trace) {
        if (request.gender() != null && !List.of("MALE", "FEMALE", "OTHER").contains(request.gender()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GENDER", "Giới tính không hợp lệ.");
        UUID id = UUID.randomUUID();
        String code = "BN-" + id.toString().substring(0, 8).toUpperCase();
        jdbc.update("""
                INSERT INTO patients(id,patient_code,full_name,phone,email,gender,date_of_birth,address,emergency_contact,emergency_phone,allergies,medical_history,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, code, request.fullName().trim(), request.phone().trim(), blankToNull(request.email()), request.gender(),
                request.dateOfBirth(), blankToNull(request.address()), blankToNull(request.emergencyContact()), blankToNull(request.emergencyPhone()),
                blankToNull(request.allergies()), blankToNull(request.medicalHistory()), actorId);
        jdbc.update("INSERT INTO patient_audit_events(actor_user_id,actor_label,action,patient_id,request_id,metadata) VALUES (?,?,'PATIENT_CREATED',?,?,?::jsonb)",actorId,"USER:"+actorId,id,trace,"{}");
        return findSummary(id);
    }

    public PatientModels.PatientSummary findSummary(UUID id) {
        return jdbc.query("""
                SELECT id,patient_code,full_name,phone,email,date_of_birth,gender,address,emergency_contact,emergency_phone,created_at
                FROM patients WHERE id=? AND deleted_at IS NULL
                """, (rs, n) -> new PatientModels.PatientSummary(rs.getObject("id", UUID.class), rs.getString("patient_code"),
                rs.getString("full_name"), rs.getString("phone"), rs.getString("email"), rs.getObject("date_of_birth", java.time.LocalDate.class),
                rs.getString("gender"), rs.getString("address"), rs.getString("emergency_contact"), rs.getString("emergency_phone"),
                rs.getTimestamp("created_at").toInstant()), id).stream().findFirst().orElseThrow(this::notFound);
    }

    public PatientModels.PatientDetail findDetail(UUID id) {
        return jdbc.query("""
                SELECT id,patient_code,full_name,phone,email,date_of_birth,gender,address,emergency_contact,emergency_phone,allergies,medical_history,notes,created_at,updated_at
                FROM patients WHERE id=? AND deleted_at IS NULL
                """, (rs, n) -> new PatientModels.PatientDetail(rs.getObject("id", UUID.class), rs.getString("patient_code"),
                rs.getString("full_name"), rs.getString("phone"), rs.getString("email"), rs.getObject("date_of_birth", java.time.LocalDate.class),
                rs.getString("gender"), rs.getString("address"), rs.getString("emergency_contact"), rs.getString("emergency_phone"),
                rs.getString("allergies"), rs.getString("medical_history"), rs.getString("notes"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), id).stream().findFirst().orElseThrow(this::notFound);
    }

    public boolean isLinkedTo(UUID patientId, UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM patient_account_links WHERE patient_id=? AND user_id=?)", Boolean.class, patientId, userId));
    }
    public PatientModels.PatientSummary findLinkedPatient(UUID userId) {
        UUID patientId=jdbc.query("SELECT patient_id FROM patient_account_links WHERE user_id=?",(rs,n)->rs.getObject("patient_id",UUID.class),userId)
                .stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PATIENT_LINK_NOT_FOUND","Tài khoản chưa được liên kết với hồ sơ bệnh nhân."));
        return findSummary(patientId);
    }
    @Transactional
    public PatientModels.AccountLink linkAccount(UUID patientId,UUID userId,UUID actorId,String trace){
        findSummary(patientId);
        try{jdbc.update("INSERT INTO patient_account_links(patient_id,user_id,linked_by) VALUES (?,?,?)",patientId,userId,actorId);}
        catch(DuplicateKeyException ex){throw new ApiException(HttpStatus.CONFLICT,"PATIENT_ACCOUNT_LINK_CONFLICT","Hồ sơ hoặc tài khoản đã được liên kết với tài khoản khác.");}
        jdbc.update("INSERT INTO patient_audit_events(actor_user_id,actor_label,action,patient_id,request_id,metadata) VALUES (?,?,'PATIENT_ACCOUNT_LINKED',?,?,?::jsonb)",actorId,"USER:"+actorId,patientId,trace,"{}");
        return new PatientModels.AccountLink(true,userId);
    }
    public PatientModels.AccountLink accountLink(UUID patientId){
        return jdbc.query("SELECT user_id FROM patient_account_links WHERE patient_id=?",(rs,n)->new PatientModels.AccountLink(true,rs.getObject("user_id",UUID.class)),patientId).stream().findFirst().orElse(new PatientModels.AccountLink(false,null));
    }
    public boolean hasDoctorGrant(UUID patientId, UUID doctorId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM patient_access_grants WHERE patient_id=? AND doctor_id=? AND expires_at>now() AND revoked_at IS NULL)", Boolean.class, patientId, doctorId));
    }

    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND", "Không tìm thấy hồ sơ bệnh nhân."); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

