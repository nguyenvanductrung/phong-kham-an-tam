package vn.phongkham.scheduling.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.phongkham.common.ApiException;
import vn.phongkham.common.CorrelationIdFilter;
import vn.phongkham.scheduling.application.SchedulingAdminService;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/scheduling")
public class SchedulingAdminController {
    private static final Set<String> ADMIN_ROLES = Set.of("CLINIC_MANAGER", "SUPER_ADMIN");
    private final SchedulingAdminService admin;

    public SchedulingAdminController(SchedulingAdminService admin) { this.admin = admin; }

    @GetMapping("/catalog")
    public SchedulingAdminModels.CatalogSnapshot snapshot(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return admin.snapshot();
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public SchedulingAdminModels.ServiceItem createService(@Valid @RequestBody SchedulingAdminModels.ServiceRequest body,
                                                            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        requireAdmin(jwt);
        return admin.saveService(null, body, actor(jwt), correlationId(request));
    }

    @PutMapping("/services/{id}")
    public SchedulingAdminModels.ServiceItem updateService(@PathVariable UUID id, @Valid @RequestBody SchedulingAdminModels.ServiceRequest body,
                                                            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        requireAdmin(jwt);
        return admin.saveService(id, body, actor(jwt), correlationId(request));
    }

    @PostMapping("/chairs")
    @ResponseStatus(HttpStatus.CREATED)
    public SchedulingAdminModels.ChairItem createChair(@Valid @RequestBody SchedulingAdminModels.ChairRequest body,
                                                        @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        requireAdmin(jwt);
        return admin.saveChair(null, body, actor(jwt), correlationId(request));
    }

    @PutMapping("/chairs/{id}")
    public SchedulingAdminModels.ChairItem updateChair(@PathVariable UUID id, @Valid @RequestBody SchedulingAdminModels.ChairRequest body,
                                                        @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        requireAdmin(jwt);
        return admin.saveChair(id, body, actor(jwt), correlationId(request));
    }

    @PostMapping("/working-hours")
    @ResponseStatus(HttpStatus.CREATED)
    public SchedulingAdminModels.WorkingHourItem addWorkingHour(@Valid @RequestBody SchedulingAdminModels.WorkingHourRequest body,
                                                                 @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        requireAdmin(jwt);
        return admin.addWorkingHour(body, actor(jwt), correlationId(request));
    }

    @DeleteMapping("/working-hours/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkingHour(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        requireAdmin(jwt);
        admin.deleteWorkingHour(id, actor(jwt), correlationId(request));
    }

    @PostMapping("/doctor-leaves")
    @ResponseStatus(HttpStatus.CREATED)
    public SchedulingAdminModels.LeaveItem requestLeave(@Valid @RequestBody SchedulingAdminModels.LeaveRequest body,
                                                         @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        requireAdmin(jwt);
        return admin.requestLeave(body, actor(jwt), correlationId(request));
    }

    @PostMapping("/doctor-leaves/{id}/review")
    public SchedulingAdminModels.LeaveItem reviewLeave(@PathVariable UUID id, @Valid @RequestBody SchedulingAdminModels.LeaveReviewRequest body,
                                                        @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        requireAdmin(jwt);
        return admin.reviewLeave(id, body, actor(jwt), correlationId(request));
    }

    @PostMapping("/clinic-closures")
    @ResponseStatus(HttpStatus.CREATED)
    public SchedulingAdminModels.ClosureItem addClosure(@Valid @RequestBody SchedulingAdminModels.ClosureRequest body,
                                                         @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        requireAdmin(jwt);
        return admin.addClosure(body, actor(jwt), correlationId(request));
    }

    @DeleteMapping("/clinic-closures/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClosure(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        requireAdmin(jwt);
        admin.deleteClosure(id, actor(jwt), correlationId(request));
    }

    private static void requireAdmin(Jwt jwt) {
        if (!roles(jwt).stream().anyMatch(ADMIN_ROLES::contains))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ quản lý phòng khám mới được cấu hình lịch và danh mục.");
    }
    private static Set<String> roles(Jwt jwt) {
        var claim = jwt.getClaimAsStringList("roles");
        return claim == null ? Set.of() : Set.copyOf(claim);
    }
    private static UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.HEADER);
        return value == null ? UUID.randomUUID().toString() : value.toString();
    }
}
