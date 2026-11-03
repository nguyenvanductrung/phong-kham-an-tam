package vn.phongkham.clinical.persistence;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.phongkham.clinical.api.ClinicalModels;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.phongkham.clinical.persistence.ClinicalFileDao.*;

@Repository
public class JdbcClinicalFileDao implements ClinicalFileDao {
    private final JdbcTemplate jdbc;
    public JdbcClinicalFileDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<RecordOwner> findRecordOwner(UUID record) {
        return jdbc.query("SELECT patient_id,doctor_id FROM clinical_records WHERE id=?", (rs, row) -> new RecordOwner(rs.getObject("patient_id", UUID.class), rs.getObject("doctor_id", UUID.class)), new Object[]{record});
    }

    @Override
    public int insertFile(UUID id, UUID patient, UUID record, String kind, String objectKey, String name, String mime, long bytes, String checksum, UUID actor) {
        return jdbc.update("INSERT INTO patient_files(id,patient_id,record_id,file_kind,object_key,original_filename,mime_type,byte_size,checksum_sha256,uploaded_by) VALUES (?,?,?,?,?,?,?,?,?,?)", new Object[]{id, patient, record, kind, objectKey, name, mime, bytes, checksum, actor});
    }

    @Override
    public List<ClinicalModels.FileResponse> listFiles(UUID record) {
        return jdbc.query("SELECT id,record_id,file_kind,original_filename,mime_type,byte_size,created_at FROM patient_files WHERE record_id=? ORDER BY created_at DESC", (rs, row) -> new ClinicalModels.FileResponse(rs.getObject("id", UUID.class), rs.getObject("record_id", UUID.class), rs.getString("file_kind"), rs.getString("original_filename"), rs.getString("mime_type"), rs.getLong("byte_size"), rs.getTimestamp("created_at").toInstant()), new Object[]{record});
    }

    @Override
    public List<FileObject> findFileObject(UUID id) {
        return jdbc.query("SELECT f.object_key,f.original_filename,f.mime_type,r.id AS record_id FROM patient_files f JOIN clinical_records r ON r.id=f.record_id WHERE f.id=?", (rs, row) -> new FileObject(rs.getString("object_key"), rs.getString("original_filename"), rs.getString("mime_type"), rs.getObject("record_id", UUID.class)), new Object[]{id});
    }

    @Override
    public List<RecordAccess> findRecordAccess(UUID record) {
        return jdbc.query("SELECT patient_id,doctor_id,status FROM clinical_records WHERE id=?", (rs, row) -> new RecordAccess(rs.getObject("patient_id", UUID.class), rs.getObject("doctor_id", UUID.class), rs.getString("status")), new Object[]{record});
    }

    @Override
    public ClinicalModels.FileResponse findFile(UUID id) {
        return jdbc.queryForObject("SELECT id,record_id,file_kind,original_filename,mime_type,byte_size,created_at FROM patient_files WHERE id=?", (rs, row) -> new ClinicalModels.FileResponse(rs.getObject("id", UUID.class), rs.getObject("record_id", UUID.class), rs.getString("file_kind"), rs.getString("original_filename"), rs.getString("mime_type"), rs.getLong("byte_size"), rs.getTimestamp("created_at").toInstant()), new Object[]{id});
    }

    @Override
    public int insertAudit(UUID actor, String label, String action, UUID entity, String trace, String metadata) {
        return jdbc.update("INSERT INTO audit_events(actor_user_id,actor_label,action,entity_type,entity_id,request_id,metadata) VALUES (?,? ,?, 'PatientFile', ?,?,?::jsonb)", new Object[]{actor, label, action, entity, trace, metadata});
    }
}
