package vn.phongkham.identity.persistence;

import vn.phongkham.identity.admin.UserAdminModels;
import java.time.*;
import java.util.*;

/** Persistence contract; SQL is implemented by JdbcUserAdminDao. */
public interface UserAdminDao {
    List<UserAdminModels.UserResponse> searchUsers(String emptyQuery, String emailQuery, String nameQuery);
    int insertUser(UUID id, String email, String passwordHash, String name);
    int insertUserRole(UUID user, String role, UUID actor);
    int deleteUserRoles(UUID user);
    int insertReplacementRole(UUID user, String role, UUID actor);
    int updateUserStatus(String status, UUID user);
    int revokeUserTokens(UUID user);
    List<UUID> lockUser(UUID user);
    List<UserAdminModels.UserResponse> findUser(UUID user);
    Integer countOtherActiveSuperAdmins(UUID excluded);
    int insertAudit(UUID actor, String label, String action, UUID target, String trace, String metadata);

}
