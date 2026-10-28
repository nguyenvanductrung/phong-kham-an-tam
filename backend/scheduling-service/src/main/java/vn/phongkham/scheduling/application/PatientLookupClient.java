package vn.phongkham.scheduling.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import vn.phongkham.common.ApiException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class PatientLookupClient {
    private final RestClient client;
    public PatientLookupClient(RestClient.Builder builder, @Value("${app.patient-service-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }
    public PatientSummary requireExists(UUID id, String accessToken) {
        return lookup(id, accessToken);
    }
    public PatientSummary requireOwns(UUID id, String accessToken) {
        return lookup(id, accessToken);
    }
    private PatientSummary lookup(UUID id, String accessToken) {
        try {
            PatientSummary patient = client.get().uri("/api/v1/patients/{id}", id)
                    .header("Authorization", "Bearer " + accessToken).retrieve().body(PatientSummary.class);
            if (patient == null) throw missing();
            return patient;
        } catch (HttpClientErrorException.Forbidden ex) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PATIENT_ACCESS_DENIED", "Bạn không có quyền truy cập hồ sơ bệnh nhân này.");
        } catch (HttpClientErrorException.NotFound ex) {
            throw missing();
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PATIENT_SERVICE_UNAVAILABLE", "Chưa thể xác nhận hồ sơ bệnh nhân. Vui lòng thử lại.");
        }
    }
    public PatientSummary findCurrentPatient(String accessToken) {
        try {
            PatientSummary patient=client.get().uri("/api/v1/patients/me").header("Authorization","Bearer "+accessToken)
                    .retrieve().body(PatientSummary.class);
            if(patient==null) throw missing();
            return patient;
        } catch(RestClientException ex) {
            throw new ApiException(HttpStatus.FORBIDDEN,"PATIENT_LINK_NOT_FOUND","Tài khoản chưa liên kết hồ sơ bệnh nhân.");
        }
    }
    private static ApiException missing() { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PATIENT_REFERENCE_INVALID", "Hồ sơ bệnh nhân không tồn tại."); }
    public record PatientSummary(UUID id,String patientCode,String fullName,String phone,String email,LocalDate dateOfBirth,
                                 String gender,String address,String emergencyContact,String emergencyPhone,Instant createdAt) {}
}

