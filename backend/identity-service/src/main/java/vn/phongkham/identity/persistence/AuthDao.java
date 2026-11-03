package vn.phongkham.identity.persistence;

import java.time.*;
import java.util.*;

/** Persistence contract; SQL is implemented by JdbcAuthDao. */
public interface AuthDao {
    List<Map<String, Object>> findUserForLogin(String email);
    int recordFailedLogin(UUID user);
    int recordSuccessfulLogin(UUID user);
    List<String> findRoles(UUID user);
    List<TokenRow> findRefreshToken(byte[] hash);
    int revokeFamily(UUID family);
    int revokeToken(UUID token);
    int consumeToken(UUID token);
    Map<String, Object> findUser(UUID user);
    int revokeExpiredFamily(UUID family);
    List<String> findCurrentRoles(UUID user);
    int revokePresentedToken(byte[] hash);
    int insertRefreshToken(UUID family, UUID user, byte[] hash, OffsetDateTime expiresAt);
    record TokenRow(UUID id, UUID familyId, UUID userId, Instant expiresAt, Instant usedAt, Instant revokedAt) {}
}
