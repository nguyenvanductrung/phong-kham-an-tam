package vn.phongkham.identity.admin;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Application use cases consumed by controllers and other services. */
public interface UserAdminService {
    List<UserAdminModels.UserResponse> search(String query);
    UserAdminModels.UserResponse get(UUID id);
    UserAdminModels.UserResponse create(UserAdminModels.CreateUserRequest request,UUID actor,Set<String> actorRoles,String trace);
    UserAdminModels.UserResponse updateRoles(UUID id,UserAdminModels.RolesRequest request,UUID actor,Set<String> actorRoles,String trace);
    UserAdminModels.UserResponse updateStatus(UUID id,UserAdminModels.StatusRequest request,UUID actor,Set<String> actorRoles,String trace);

}
