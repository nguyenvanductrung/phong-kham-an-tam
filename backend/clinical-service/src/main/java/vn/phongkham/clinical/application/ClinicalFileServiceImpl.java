package vn.phongkham.clinical.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.phongkham.clinical.persistence.ClinicalFileDao;
import vn.phongkham.clinical.persistence.ClinicalFileDao.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import vn.phongkham.clinical.api.ClinicalModels;
import vn.phongkham.common.ApiException;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ClinicalFileServiceImpl implements ClinicalFileService {
    private static final List<String> ALLOWED_TYPES = List.of("application/pdf", "image/jpeg", "image/png", "image/webp");
    private final ClinicalFileDao dao;
    private final S3Client storage;
    private final String bucket;
    private final long maxBytes;

    public ClinicalFileServiceImpl(ClinicalFileDao dao, S3Client storage,
                        @Value("${app.files.bucket}") String bucket,
                        @Value("${app.files.max-bytes}") long maxBytes) {
        this.dao = dao;
        this.storage = storage;
        this.bucket = bucket;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public ClinicalModels.FileResponse upload(UUID recordId, MultipartFile file, String fileKind, UUID actor, String correlationId) {
        var parent = dao.findRecordOwner(recordId)
                .stream().findFirst().orElseThrow(this::notFound);
        if (!parent.doctorId().equals(actor)) throw forbidden();
        if (file == null || file.isEmpty() || file.getSize() > maxBytes)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_SIZE", "Tệp phải lớn hơn 0 và không vượt quá giới hạn cho phép.");
        String contentType = file.getContentType() == null ? "" : file.getContentType().split(";", 2)[0].trim().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType))
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE", "Chỉ hỗ trợ PDF, JPEG, PNG hoặc WebP.");
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (Exception error) { throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_READ_FAILED", "Không đọc được tệp tải lên."); }
        if (!matchesSignature(contentType, bytes))
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_CONTENT_TYPE_MISMATCH", "Nội dung tệp không khớp định dạng đã khai báo.");
        String checksum = sha256(bytes);
        UUID id = UUID.randomUUID();
        String key = "records/" + recordId + "/" + id;
        String filename = safeFilename(file.getOriginalFilename());
        storage.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).contentLength((long) bytes.length).build(), RequestBody.fromBytes(bytes));
        try {
            dao.insertFile(id, parent.patientId(), recordId, safeKind(fileKind), key, filename, contentType, bytes.length, checksum, actor);
            audit(actor, id, correlationId, "CLINICAL_FILE_UPLOADED");
        } catch (RuntimeException error) {
            storage.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            throw error;
        }
        return find(id);
    }

    @Transactional(readOnly = true)
    public List<ClinicalModels.FileResponse> list(UUID recordId, UUID actor, boolean patient, UUID patientId) {
        authorize(recordId, actor, patient, patientId);
        return dao.listFiles(recordId);
    }

    public Download download(UUID fileId, UUID actor, boolean patient, UUID patientId) {
        var file = dao.findFileObject(fileId)
                .stream().findFirst().orElseThrow(this::notFound);
        authorize(file.recordId(), actor, patient, patientId);
        InputStream stream = storage.getObject(GetObjectRequest.builder().bucket(bucket).key(file.key()).build());
        return new Download(file.name(), file.mimeType(), stream);
    }

    private void authorize(UUID recordId, UUID actor, boolean patient, UUID patientId) {
        var owners = dao.findRecordAccess(recordId)
                .stream().findFirst().orElseThrow(this::notFound);
        if (patient) {
            if (!owners.patientId().equals(patientId) || !"SIGNED".equals(owners.status())) throw notFound();
        } else if (!owners.doctorId().equals(actor)) throw forbidden();
    }

    private ClinicalModels.FileResponse find(UUID id) {
        return dao.findFile(id);
    }

    private void audit(UUID actor, UUID fileId, String trace, String action) {
        dao.insertAudit(actor, "DOCTOR", action, fileId, trace, "{}");
    }

    private static String safeFilename(String value) {
        if (value == null || value.isBlank()) return "clinical-file";
        String name = value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
        if (name.isBlank()) return "clinical-file";
        return name.length() > 180 ? name.substring(name.length() - 180) : name;
    }

    private static String safeKind(String value) {
        if (value == null || !value.matches("[A-Z_]{2,32}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_KIND", "Loại tệp không hợp lệ.");
        return value;
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static boolean matchesSignature(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "application/pdf" -> startsWith(bytes, 0x25, 0x50, 0x44, 0x46, 0x2D);
            case "image/jpeg" -> startsWith(bytes, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> bytes.length >= 12 && startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
                    && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
            default -> false;
        };
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if ((bytes[i] & 0xFF) != signature[i]) return false;
        return true;
    }

    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "FILE_OR_RECORD_NOT_FOUND", "Không tìm thấy tệp hoặc bệnh án."); }
    private ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền truy cập tệp bệnh án này."); }




}
