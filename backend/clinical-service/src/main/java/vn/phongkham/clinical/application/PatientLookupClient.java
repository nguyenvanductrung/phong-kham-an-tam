package vn.phongkham.clinical.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.phongkham.common.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class PatientLookupClient {
    private final RestClient client;
    public PatientLookupClient(RestClient.Builder builder,@Value("${app.patient-service-url}") String baseUrl){client=builder.baseUrl(baseUrl).build();}
    public PatientSummary current(String token){
        try{return client.get().uri("/api/v1/patients/me").header("Authorization","Bearer "+token).retrieve().body(PatientSummary.class);}
        catch(RestClientException ex){throw new ApiException(HttpStatus.FORBIDDEN,"PATIENT_LINK_NOT_FOUND","Tài khoản chưa được liên kết hồ sơ.");}
    }
    public void requirePatientAccess(UUID id,String token){
        try{client.get().uri("/api/v1/patients/{id}/clinical-summary",id).header("Authorization","Bearer "+token).retrieve().toBodilessEntity();}
        catch(RestClientException ex){throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Bạn không có quyền xem dữ liệu lâm sàng này.");}
    }
    public record PatientSummary(UUID id,String patientCode,String fullName,String phone,String email,LocalDate dateOfBirth,
                                 String gender,String address,String emergencyContact,String emergencyPhone,Instant createdAt){}
}

