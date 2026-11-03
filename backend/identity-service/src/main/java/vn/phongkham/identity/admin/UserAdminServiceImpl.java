package vn.phongkham.identity.admin;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import vn.phongkham.identity.persistence.UserAdminDao;
import vn.phongkham.identity.persistence.UserAdminDao.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.phongkham.common.ApiException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserAdminServiceImpl implements UserAdminService {
    private static final Set<String> ALL_ROLES=Set.of("SUPER_ADMIN","CLINIC_MANAGER","DOCTOR","RECEPTIONIST","ASSISTANT","PATIENT");
    private static final Set<String> MANAGER_ROLES=Set.of("CLINIC_MANAGER","DOCTOR","RECEPTIONIST","ASSISTANT","PATIENT");
    private final UserAdminDao dao;
    private final PasswordEncoder passwords;
    public UserAdminServiceImpl(UserAdminDao dao,PasswordEncoder passwords){this.dao=dao;this.passwords=passwords;}

    public List<UserAdminModels.UserResponse> search(String query){
        String q=query==null?"":query.trim();
        return dao.searchUsers(q, "%"+q+"%", "%"+q+"%");
    }
    public UserAdminModels.UserResponse get(UUID id){return find(id);}

    @Transactional
    public UserAdminModels.UserResponse create(UserAdminModels.CreateUserRequest request,UUID actor,Set<String> actorRoles,String trace){
        validateRoles(request.roles(),actorRoles);
        UUID id=UUID.randomUUID();
        try { dao.insertUser(id, request.email().trim().toLowerCase(java.util.Locale.ROOT), passwords.encode(request.password()), request.fullName().trim()); }
        catch(DuplicateKeyException ex){throw new ApiException(HttpStatus.CONFLICT,"EMAIL_IN_USE","Email này đã được sử dụng.");}
        for(String role:request.roles().stream().distinct().sorted().toList())dao.insertUserRole(id, role, actor);
        audit(actor,actorRoles,"USER_CREATED",id,trace);
        return find(id);
    }

    @Transactional
    public UserAdminModels.UserResponse updateRoles(UUID id,UserAdminModels.RolesRequest request,UUID actor,Set<String> actorRoles,String trace){
        var current=findForUpdate(id);
        var roles=request.roles().stream().distinct().sorted().toList();
        validateRoles(roles,actorRoles);
        if(!actorRoles.contains("SUPER_ADMIN") && current.roles().contains("SUPER_ADMIN"))throw forbidden();
        if(current.roles().contains("SUPER_ADMIN")&&!roles.contains("SUPER_ADMIN"))requireAnotherActiveSuperAdmin(id);
        dao.deleteUserRoles(id);
        for(String role:roles)dao.insertReplacementRole(id, role, actor);
        audit(actor,actorRoles,"USER_ROLES_UPDATED",id,trace);
        return find(id);
    }

    @Transactional
    public UserAdminModels.UserResponse updateStatus(UUID id,UserAdminModels.StatusRequest request,UUID actor,Set<String> actorRoles,String trace){
        String next=request.status().trim().toUpperCase(java.util.Locale.ROOT);
        if(!Set.of("ACTIVE","SUSPENDED","DISABLED").contains(next))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_USER_STATUS","Trạng thái tài khoản không hợp lệ.");
        var current=findForUpdate(id);
        if(current.roles().contains("SUPER_ADMIN")&&!actorRoles.contains("SUPER_ADMIN"))throw forbidden();
        if(current.roles().contains("SUPER_ADMIN")&&!next.equals("ACTIVE"))requireAnotherActiveSuperAdmin(id);
        dao.updateUserStatus(next, id);
        if(!next.equals("ACTIVE"))dao.revokeUserTokens(id);
        audit(actor,actorRoles,"USER_STATUS_"+next,id,trace);
        return find(id);
    }

    private UserAdminModels.UserResponse findForUpdate(UUID id){
        dao.lockUser(id).stream().findFirst().orElseThrow(this::notFound);
        return find(id);
    }
    private UserAdminModels.UserResponse find(UUID id){return searchById(id);}
    private UserAdminModels.UserResponse searchById(UUID id){
        return dao.findUser(id).stream().findFirst().orElseThrow(this::notFound);
    }
    private void validateRoles(List<String> roles,Set<String> actorRoles){
        Set<String> allowed=actorRoles.contains("SUPER_ADMIN")?ALL_ROLES:MANAGER_ROLES;
        if(roles.isEmpty()||roles.stream().anyMatch(role->!ALL_ROLES.contains(role)||!allowed.contains(role)))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ROLE_ASSIGNMENT","Vai trò được chọn không hợp lệ hoặc vượt quyền quản lý.");
    }
    private void requireAnotherActiveSuperAdmin(UUID target){
        int count=dao.countOtherActiveSuperAdmins(target);
        if(count==0)throw new ApiException(HttpStatus.CONFLICT,"LAST_SUPER_ADMIN","Không thể vô hiệu hóa hoặc gỡ vai trò của quản trị viên SUPER_ADMIN hoạt động cuối cùng.");
    }
    private void audit(UUID actor,Set<String> roles,String action,UUID target,String trace){
        dao.insertAudit(actor, roles.toString(), action, target, trace, "{}");
    }
    private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","Không tìm thấy tài khoản.");}
    private ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Bạn không có quyền quản lý tài khoản này.");}
}
