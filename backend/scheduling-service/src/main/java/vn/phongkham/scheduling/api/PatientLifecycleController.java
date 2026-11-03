package vn.phongkham.scheduling.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.phongkham.common.ApiException;
import vn.phongkham.scheduling.application.PatientLifecycleService;
import java.util.Set;
import java.util.UUID;

/** Service-to-service endpoints, deliberately absent from gateway routes. */
@RestController
@RequestMapping("/internal/patients/{id}/lifecycle")
public class PatientLifecycleController {
    private final PatientLifecycleService lifecycle;
    public PatientLifecycleController(PatientLifecycleService lifecycle) { this.lifecycle=lifecycle; }
    public record Change(@NotNull UUID operationId,@NotNull @Pattern(regexp="ACTIVE|ARCHIVED|DELETED") String state) {}
    @PutMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@PathVariable UUID id,@Valid @RequestBody Change request,@AuthenticationPrincipal Jwt jwt) {
        requireStaff(jwt);lifecycle.change(id,request.operationId(),request.state());
    }
    @PostMapping("/rollback") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rollback(@PathVariable UUID id,@Valid @RequestBody Change request,@AuthenticationPrincipal Jwt jwt) {
        requireStaff(jwt);lifecycle.compensate(id,request.operationId(),request.state());
    }
    private static void requireStaff(Jwt jwt) {
        var roles=jwt.getClaimAsStringList("roles");
        if(roles==null||roles.stream().noneMatch(Set.of("RECEPTIONIST","CLINIC_MANAGER","SUPER_ADMIN")::contains))
            throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Bạn không có quyền thay đổi trạng thái hồ sơ.");
    }
}
