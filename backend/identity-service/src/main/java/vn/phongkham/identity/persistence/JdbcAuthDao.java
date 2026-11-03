package vn.phongkham.identity.persistence;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.phongkham.identity.persistence.AuthDao.*;

@Repository
public class JdbcAuthDao implements AuthDao {
    private final JdbcTemplate jdbc;
    public JdbcAuthDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Map<String, Object>> findUserForLogin(String email) {
        return jdbc.queryForList("""
                    SELECT u.id, u.password_hash, u.full_name, u.status, u.locked_until
                    FROM users u WHERE lower(u.email)=lower(?) FOR UPDATE
                    """, new Object[]{email});
    }

    @Override
    public int recordFailedLogin(UUID user) {
        return jdbc.update("UPDATE users SET failed_login_count=failed_login_count+1, locked_until=CASE WHEN failed_login_count+1>=8 THEN now()+INTERVAL '15 minutes' ELSE locked_until END, updated_at=now() WHERE id=?", new Object[]{user});
    }

    @Override
    public int recordSuccessfulLogin(UUID user) {
        return jdbc.update("UPDATE users SET failed_login_count=0, locked_until=NULL, last_login_at=now(), updated_at=now() WHERE id=?", new Object[]{user});
    }

    @Override
    public List<String> findRoles(UUID user) {
        return jdbc.queryForList("SELECT role_code FROM user_roles WHERE user_id=? ORDER BY role_code", String.class, new Object[]{user});
    }

    @Override
    public List<TokenRow> findRefreshToken(byte[] hash) {
        return jdbc.query("SELECT id, family_id, user_id, expires_at, used_at, revoked_at FROM refresh_tokens WHERE token_hash=? FOR UPDATE", (rs, row) -> new TokenRow(rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class),
                            rs.getObject("user_id", UUID.class), rs.getTimestamp("expires_at").toInstant(),
                            rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant(),
                            rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()), new Object[]{hash});
    }

    @Override
    public int revokeFamily(UUID family) {
        return jdbc.update("UPDATE refresh_tokens SET revoked_at=COALESCE(revoked_at, now()) WHERE family_id=?", new Object[]{family});
    }

    @Override
    public int revokeToken(UUID token) {
        return jdbc.update("UPDATE refresh_tokens SET revoked_at=now() WHERE id=?", new Object[]{token});
    }

    @Override
    public int consumeToken(UUID token) {
        return jdbc.update("UPDATE refresh_tokens SET used_at=now(), revoked_at=now() WHERE id=?", new Object[]{token});
    }

    @Override
    public Map<String, Object> findUser(UUID user) {
        return jdbc.queryForMap("SELECT full_name, status FROM users WHERE id=?", new Object[]{user});
    }

    @Override
    public int revokeExpiredFamily(UUID family) {
        return jdbc.update("UPDATE refresh_tokens SET revoked_at=COALESCE(revoked_at,now()) WHERE family_id=?", new Object[]{family});
    }

    @Override
    public List<String> findCurrentRoles(UUID user) {
        return jdbc.queryForList("SELECT role_code FROM user_roles WHERE user_id=? ORDER BY role_code", String.class, new Object[]{user});
    }

    @Override
    public int revokePresentedToken(byte[] hash) {
        return jdbc.update("UPDATE refresh_tokens SET revoked_at=COALESCE(revoked_at, now()) WHERE token_hash=?", new Object[]{hash});
    }

    @Override
    public int insertRefreshToken(UUID family, UUID user, byte[] hash, OffsetDateTime expiresAt) {
        return jdbc.update("INSERT INTO refresh_tokens(family_id,user_id,token_hash,expires_at) VALUES(?,?,?,?)", new Object[]{family, user, hash, expiresAt});
    }
}
