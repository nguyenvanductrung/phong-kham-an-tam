package vn.phongkham.identity.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.phongkham.common.ApiException;
import vn.phongkham.common.CorrelationIdFilter;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {
    private final UserAdminService users;
    public UserAdminController(UserAdminService users){this.users=users;}
    @GetMapping public Object search(@RequestParam(defaultValue="") String q,@AuthenticationPrincipal Jwt jwt){requireManager(jwt);return users.search(q);}
    @GetMapping("/{id}") public UserAdminModels.UserResponse get(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){requireManager(jwt);return users.get(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public UserAdminModels.UserResponse create(@Valid @RequestBody UserAdminModels.CreateUserRequest body,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        Set<String> roles=roles(jwt);requireManager(jwt);return users.create(body,actor(jwt),roles,trace(request));
    }
    @PutMapping("/{id}/roles")
    public UserAdminModels.UserResponse roles(@PathVariable UUID id,@Valid @RequestBody UserAdminModels.RolesRequest body,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        Set<String> roles=roles(jwt);requireManager(jwt);return users.updateRoles(id,body,actor(jwt),roles,trace(request));
    }
    @PutMapping("/{id}/status")
    public UserAdminModels.UserResponse status(@PathVariable UUID id,@Valid @RequestBody UserAdminModels.StatusRequest body,@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){
        Set<String> roles=roles(jwt);requireManager(jwt);return users.updateStatus(id,body,actor(jwt),roles,trace(request));
    }
    private static UUID actor(Jwt jwt){return UUID.fromString(jwt.getSubject());}
    private static Set<String> roles(Jwt jwt){var values=jwt.getClaimAsStringList("roles");return values==null?Set.of():Set.copyOf(values);}
    private static void requireManager(Jwt jwt){if(roles(jwt).stream().noneMatch(Set.of("SUPER_ADMIN","CLINIC_MANAGER")::contains))throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ quản trị viên hoặc quản lý phòng khám được quản lý tài khoản.");}
    private static String trace(HttpServletRequest request){Object value=request.getAttribute(CorrelationIdFilter.HEADER);return value==null?UUID.randomUUID().toString():value.toString();}
}
