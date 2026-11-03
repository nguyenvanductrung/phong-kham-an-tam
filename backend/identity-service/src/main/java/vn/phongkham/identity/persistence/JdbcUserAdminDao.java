package vn.phongkham.identity.persistence;

import vn.phongkham.identity.admin.UserAdminModels;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.phongkham.identity.persistence.UserAdminDao.*;

@Repository
public class JdbcUserAdminDao implements UserAdminDao {
    private final JdbcTemplate jdbc;
    public JdbcUserAdminDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<UserAdminModels.UserResponse> searchUsers(String emptyQuery, String emailQuery, String nameQuery) {
        return jdbc.query("""
                SELECT u.id,u.email,u.full_name,u.status,u.created_at,
                       COALESCE(array_agg(ur.role_code) FILTER (WHERE ur.role_code IS NOT NULL),'{}') AS roles
                FROM users u LEFT JOIN user_roles ur ON ur.user_id=u.id
                WHERE (?='' OR u.email ILIKE ? OR u.full_name ILIKE ?)
                GROUP BY u.id ORDER BY u.full_name,u.id LIMIT 200
                """, (rs,n)->new UserAdminModels.UserResponse(rs.getObject("id",UUID.class).toString(),rs.getString("email"),
                rs.getString("full_name"),rs.getString("status"),java.util.Arrays.asList((String[])rs.getArray("roles").getArray()),
                rs.getTimestamp("created_at").toInstant()), new Object[]{emptyQuery, emailQuery, nameQuery});
    }

    @Override
    public int insertUser(UUID id, String email, String passwordHash, String name) {
        return jdbc.update("INSERT INTO users(id,email,password_hash,full_name) VALUES (?,?,?,?)", new Object[]{id, email, passwordHash, name});
    }

    @Override
    public int insertUserRole(UUID user, String role, UUID actor) {
        return jdbc.update("INSERT INTO user_roles(user_id,role_code,assigned_by) VALUES (?,?,?)", new Object[]{user, role, actor});
    }

    @Override
    public int deleteUserRoles(UUID user) {
        return jdbc.update("DELETE FROM user_roles WHERE user_id=?", new Object[]{user});
    }

    @Override
    public int insertReplacementRole(UUID user, String role, UUID actor) {
        return jdbc.update("INSERT INTO user_roles(user_id,role_code,assigned_by) VALUES (?,?,?)", new Object[]{user, role, actor});
    }

    @Override
    public int updateUserStatus(String status, UUID user) {
        return jdbc.update("UPDATE users SET status=?,updated_at=now() WHERE id=?", new Object[]{status, user});
    }

    @Override
    public int revokeUserTokens(UUID user) {
        return jdbc.update("UPDATE refresh_tokens SET revoked_at=COALESCE(revoked_at,now()) WHERE user_id=?", new Object[]{user});
    }

    @Override
    public List<UUID> lockUser(UUID user) {
        return jdbc.query("SELECT id FROM users WHERE id=? FOR UPDATE", (rs,n)->rs.getObject(1,UUID.class), new Object[]{user});
    }

    @Override
    public List<UserAdminModels.UserResponse> findUser(UUID user) {
        return jdbc.query("""
                SELECT u.id,u.email,u.full_name,u.status,u.created_at,
                       COALESCE(array_agg(ur.role_code) FILTER (WHERE ur.role_code IS NOT NULL),'{}') AS roles
                FROM users u LEFT JOIN user_roles ur ON ur.user_id=u.id WHERE u.id=? GROUP BY u.id
                """, (rs,n)->new UserAdminModels.UserResponse(rs.getObject("id",UUID.class).toString(),rs.getString("email"),rs.getString("full_name"),rs.getString("status"),
                java.util.Arrays.asList((String[])rs.getArray("roles").getArray()),rs.getTimestamp("created_at").toInstant()), new Object[]{user});
    }

    @Override
    public Integer countOtherActiveSuperAdmins(UUID excluded) {
        return jdbc.queryForObject("SELECT count(*) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.status='ACTIVE' AND r.role_code='SUPER_ADMIN' AND u.id<>?", Integer.class, new Object[]{excluded});
    }

    @Override
    public int insertAudit(UUID actor, String label, String action, UUID target, String trace, String metadata) {
        return jdbc.update("INSERT INTO identity_audit_events(actor_user_id,actor_label,action,target_user_id,request_id,metadata) VALUES (?,?,?,?,?,?::jsonb)", new Object[]{actor, label, action, target, trace, metadata});
    }
}
