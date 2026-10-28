package vn.phongkham.patient.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.phongkham.common.ApiException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Component
public class IdentityLookupClient {
    private final RestClient client;
    public IdentityLookupClient(RestClient.Builder builder,@Value("${app.identity-service-url:http://localhost:8081}") String baseUrl){client=builder.baseUrl(baseUrl).build();}
    public void requirePatientAccount(UUID userId,String token){
        try{
            UserSummary user=client.get().uri("/api/v1/admin/users/{id}",userId).header("Authorization","Bearer "+token).retrieve().body(UserSummary.class);
            if(user==null||!user.status().equals("ACTIVE")||!Arrays.asList(user.roles()).contains("PATIENT"))throw invalid();
        }catch(org.springframework.web.client.HttpClientErrorException.Forbidden ex){throw new ApiException(HttpStatus.FORBIDDEN,"ACCOUNT_LINK_FORBIDDEN","Bạn không có quyền gắn tài khoản bệnh nhân này.");}
        catch(org.springframework.web.client.HttpClientErrorException.NotFound ex){throw invalid();}
        catch(RestClientException ex){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"IDENTITY_SERVICE_UNAVAILABLE","Chưa thể xác minh tài khoản. Vui lòng thử lại.");}
    }
    private static ApiException invalid(){return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"PATIENT_ACCOUNT_INVALID","Chỉ liên kết được tài khoản đang hoạt động có vai trò bệnh nhân.");}
    public record UserSummary(String id,String email,String fullName,String status,String[] roles,Instant createdAt){}
}
