package vn.phongkham.patient.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import vn.phongkham.common.ApiException;
import java.util.Map;
import java.util.UUID;

@Component
public class PatientLifecycleClient {
    private static final Logger LOG=LoggerFactory.getLogger(PatientLifecycleClient.class);
    private final RestClient client;
    public PatientLifecycleClient(RestClient.Builder builder,@Value("${app.scheduling-service-url}") String baseUrl) {
        var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);factory.setReadTimeout(3000);client=builder.clone().requestFactory(factory).baseUrl(baseUrl).build();
    }
    public void change(UUID id,UUID operation,String state,String token) {
        try {
            client.put().uri("/internal/patients/{id}/lifecycle",id).header("Authorization","Bearer "+token)
                    .body(Map.of("operationId",operation,"state",state)).retrieve().toBodilessEntity();
        } catch(HttpClientErrorException.Conflict ex) {
            throw new ApiException(HttpStatus.CONFLICT,"PATIENT_HAS_APPOINTMENTS",state.equals("DELETED")
                    ?"Hồ sơ đã có lịch hẹn. Hãy lưu trữ thay vì xóa.":"Hãy hoàn tất hoặc hủy các lịch hẹn đang chờ trước khi lưu trữ hồ sơ.");
        } catch(RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PATIENT_LIFECYCLE_UNAVAILABLE","Chưa thể xác nhận trạng thái lịch hẹn. Vui lòng thử lại.");
        }
    }
    public void compensate(UUID id,UUID operation,String previous,String token) {
        try {
            client.post().uri("/internal/patients/{id}/lifecycle/rollback",id).header("Authorization","Bearer "+token)
                    .body(Map.of("operationId",operation,"state",previous)).retrieve().toBodilessEntity();
        } catch(RestClientException ex) {
            LOG.error("Patient lifecycle rollback needs retry: patient={}, operation={}",id,operation);
        }
    }
}
