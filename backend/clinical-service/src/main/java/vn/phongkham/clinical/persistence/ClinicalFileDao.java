package vn.phongkham.clinical.persistence;

import java.time.*;
import vn.phongkham.clinical.api.ClinicalModels;
import java.util.*;

/** Persistence contract; SQL is implemented by JdbcClinicalFileDao. */
public interface ClinicalFileDao {
    List<RecordOwner> findRecordOwner(UUID record);
    int insertFile(UUID id, UUID patient, UUID record, String kind, String objectKey, String name, String mime, long bytes, String checksum, UUID actor);
    List<ClinicalModels.FileResponse> listFiles(UUID record);
    List<FileObject> findFileObject(UUID id);
    List<RecordAccess> findRecordAccess(UUID record);
    ClinicalModels.FileResponse findFile(UUID id);
    int insertAudit(UUID actor, String label, String action, UUID entity, String trace, String metadata);
    record RecordOwner(UUID patientId, UUID doctorId) {}
    record RecordAccess(UUID patientId, UUID doctorId, String status) {}
    record FileObject(String key, String name, String mimeType, UUID recordId) {}
}
