package vn.phongkham.clinical.application;

import org.springframework.web.multipart.MultipartFile;
import vn.phongkham.clinical.api.ClinicalModels;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/** Application use cases consumed by controllers and other services. */
public interface ClinicalFileService {
    ClinicalModels.FileResponse upload(UUID recordId, MultipartFile file, String fileKind, UUID actor, String correlationId);
    List<ClinicalModels.FileResponse> list(UUID recordId, UUID actor, boolean patient, UUID patientId);
    Download download(UUID fileId, UUID actor, boolean patient, UUID patientId);
    public record Download(String filename, String contentType, InputStream stream) {}
}
