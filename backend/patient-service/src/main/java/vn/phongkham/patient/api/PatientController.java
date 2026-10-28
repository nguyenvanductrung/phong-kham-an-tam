package vn.phongkham.patient.api;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.phongkham.common.ApiException;
import vn.phongkham.patient.application.PatientService;
import vn.phongkham.patient.application.IdentityLookupClient;
import vn.phongkham.common.CorrelationIdFilter;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {
    private static final Set<String> ADMIN = Set.of("SUPER_ADMIN", "CLINIC_MANAGER", "RECEPTIONIST");
    private final PatientService patients;
    private final IdentityLookupClient identity;
    public PatientController(PatientService patients,IdentityLookupClient identity) { this.patients = patients;this.identity=identity; }

    @GetMapping
    public PatientModels.PatientPage search(@RequestParam(defaultValue="") String q,
                                            @RequestParam(defaultValue="0") int page,
                                            @RequestParam(defaultValue="20") int size,
                                            @AuthenticationPrincipal Jwt jwt) {
        requireAny(jwt, ADMIN);
        return patients.search(q, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientModels.PatientSummary create(@Valid @RequestBody PatientModels.CreatePatientRequest request,
                                               @AuthenticationPrincipal Jwt jwt,HttpServletRequest servletRequest) {
        requireAny(jwt, Set.of("SUPER_ADMIN", "CLINIC_MANAGER", "RECEPTIONIST"));
        Object trace=servletRequest.getAttribute(CorrelationIdFilter.HEADER);
        return patients.create(request,UUID.fromString(jwt.getSubject()),trace==null?UUID.randomUUID().toString():trace.toString());
    }

    @GetMapping("/me")
    public PatientModels.PatientSummary me(@AuthenticationPrincipal Jwt jwt) {
        if(!roleSet(jwt).contains("PATIENT")) throw forbidden();
        return patients.findLinkedPatient(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{id}")
    public Object get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Set<String> roles = roleSet(jwt);
        if (roles.contains("PATIENT")) {
            if (!patients.isLinkedTo(id, UUID.fromString(jwt.getSubject()))) throw forbidden();
            return patients.findDetail(id);
        }
        if (roles.contains("DOCTOR")) {
            if (!patients.hasDoctorGrant(id, UUID.fromString(jwt.getSubject()))) throw forbidden();
            return patients.findDetail(id);
        }
        if (roles.contains("ASSISTANT")) {
            if (!patients.hasDoctorGrant(id, UUID.fromString(jwt.getSubject()))) throw forbidden();
            return patients.findSummary(id);
        }
        if (ADMIN.containsAll(roles) || roles.stream().anyMatch(ADMIN::contains)) return patients.findSummary(id);
        throw forbidden();
    }

    @GetMapping("/{id}/clinical-summary")
    public PatientModels.PatientDetail clinicalSummary(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Set<String> roles = roleSet(jwt);
        UUID userId = UUID.fromString(jwt.getSubject());
        if (roles.contains("PATIENT") && patients.isLinkedTo(id, userId)) return patients.findDetail(id);
        if (roles.contains("DOCTOR") && patients.hasDoctorGrant(id, userId)) return patients.findDetail(id);
        throw forbidden();
    }

    @GetMapping("/{id}/account-link")
    public PatientModels.AccountLink accountLink(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){
        requireAny(jwt,Set.of("SUPER_ADMIN","CLINIC_MANAGER"));return patients.accountLink(id);
    }

    @PostMapping("/{id}/account-link")
    @ResponseStatus(HttpStatus.CREATED)
    public PatientModels.AccountLink linkAccount(@PathVariable UUID id,@Valid @RequestBody PatientModels.LinkAccountRequest body,
                                                 @AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        requireAny(jwt,Set.of("SUPER_ADMIN","CLINIC_MANAGER"));identity.requirePatientAccount(body.userId(),jwt.getTokenValue());
        Object trace=request.getAttribute(CorrelationIdFilter.HEADER);
        return patients.linkAccount(id,body.userId(),UUID.fromString(jwt.getSubject()),trace==null?UUID.randomUUID().toString():trace.toString());
    }

    private static Set<String> roleSet(Jwt jwt) {
        var roles = jwt.getClaimAsStringList("roles");
        return roles == null ? Set.of() : Set.copyOf(roles);
    }
    private static void requireAny(Jwt jwt, Set<String> allowed) {
        if (roleSet(jwt).stream().noneMatch(allowed::contains)) throw forbidden();
    }
    private static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền truy cập hồ sơ này."); }
}

