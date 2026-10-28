package vn.phongkham.clinical.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.phongkham.common.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class SchedulingLookupClient {
    private final RestClient client;
    public SchedulingLookupClient(RestClient.Builder builder,@Value("${app.scheduling-service-url}") String baseUrl){client=builder.baseUrl(baseUrl).build();}
    public Appointment requireAppointment(UUID id,String token){
        try{var a=client.get().uri("/api/v1/appointments/{id}",id).header("Authorization","Bearer "+token).retrieve().body(Appointment.class);
            if(a==null)throw missing();return a;
        }catch(RestClientException e){throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"APPOINTMENT_REFERENCE_INVALID","Không thể xác nhận lượt khám. Vui lòng thử lại.");}
    }
    public ServiceItem requireService(UUID id,String token){
        try{
            var services=client.get().uri("/api/v1/catalog/services").header("Authorization","Bearer "+token).retrieve().body(ServiceItem[].class);
            if(services==null)throw new RuntimeException();
            return List.of(services).stream().filter(s->s.id().equals(id)).findFirst().orElseThrow(()->new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"SERVICE_REFERENCE_INVALID","Dịch vụ không tồn tại hoặc đã ngừng hoạt động."));
        }catch(RestClientException e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"SCHEDULING_UNAVAILABLE","Không thể đọc danh mục dịch vụ.");}
    }
    private ApiException missing(){return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"APPOINTMENT_REFERENCE_INVALID","Lịch hẹn không tồn tại.");}
    public record Appointment(UUID id,String appointmentNumber,UUID patientId,UUID doctorId,UUID chairId,Instant scheduledAt,int durationMinutes,
                              String status,String source,String serviceName,BigDecimal estimatedPrice,long version){}
    public record ServiceItem(UUID id,String code,String name,BigDecimal price,int durationMinutes){}
}

