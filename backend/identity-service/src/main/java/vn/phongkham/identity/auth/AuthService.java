package vn.phongkham.identity.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.phongkham.common.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;
    private final int accessMinutes;
    private final int refreshDays;

    public AuthService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       @Value("${security.issuer}") String issuer,
                       @Value("${security.audience}") String audience,
                       @Value("${security.access-token-minutes}") int accessMinutes,
                       @Value("${security.refresh-token-days}") int refreshDays) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.audience = audience;
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
    }

    public AuthModels.TokenResponse login(AuthModels.LoginRequest request) {
        Outcome outcome = transaction.execute(status -> {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT u.id, u.password_hash, u.full_name, u.status, u.locked_until
                    FROM users u WHERE lower(u.email)=lower(?) FOR UPDATE
                    """, request.email());
            if (rows.isEmpty()) {
                passwordEncoder.matches(request.password(), passwordEncoder.encode("invalid-account"));
                return Outcome.failure(invalidCredentials());
            }
            Map<String, Object> user = rows.getFirst();
            UUID userId = (UUID) user.get("id");
            Instant lockedUntil = user.get("locked_until") == null ? null : ((java.sql.Timestamp) user.get("locked_until")).toInstant();
            if (!"ACTIVE".equals(user.get("status")) || (lockedUntil != null && lockedUntil.isAfter(Instant.now()))
                    || !passwordEncoder.matches(request.password(), (String) user.get("password_hash"))) {
                jdbc.update("UPDATE users SET failed_login_count=failed_login_count+1, locked_until=CASE WHEN failed_login_count+1>=8 THEN now()+INTERVAL '15 minutes' ELSE locked_until END, updated_at=now() WHERE id=?", userId);
                return Outcome.failure(invalidCredentials());
            }
            jdbc.update("UPDATE users SET failed_login_count=0, locked_until=NULL, last_login_at=now(), updated_at=now() WHERE id=?", userId);
            List<String> roles = jdbc.queryForList("SELECT role_code FROM user_roles WHERE user_id=? ORDER BY role_code", String.class, userId);
            return Outcome.success(createSession(userId, (String) user.get("full_name"), roles, UUID.randomUUID()));
        });
        return unwrap(outcome);
    }

    public AuthModels.TokenResponse refresh(String presentedToken) {
        byte[] hash = sha256(presentedToken);
        Outcome outcome = transaction.execute(status -> {
            var found = jdbc.query("SELECT id, family_id, user_id, expires_at, used_at, revoked_at FROM refresh_tokens WHERE token_hash=? FOR UPDATE",
                    (rs, row) -> new TokenRow(rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class),
                            rs.getObject("user_id", UUID.class), rs.getTimestamp("expires_at").toInstant(),
                            rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant(),
                            rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()), hash);
            if (found.isEmpty()) return Outcome.failure(invalidRefresh());
            TokenRow token = found.getFirst();
            if (token.usedAt() != null || token.revokedAt() != null) {
                jdbc.update("UPDATE refresh_tokens SET revoked_at=COALESCE(revoked_at, now()) WHERE family_id=?", token.familyId());
                return Outcome.failure(new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSE", "Phiên đăng nhập đã bị thu hồi. Vui lòng đăng nhập lại."));
            }
            if (!token.expiresAt().isAfter(Instant.now())) {
                jdbc.update("UPDATE refresh_tokens SET revoked_at=now() WHERE id=?", token.id());
                return Outcome.failure(invalidRefresh());
            }
            jdbc.update("UPDATE refresh_tokens SET used_at=now(), revoked_at=now() WHERE id=?", token.id());
            var user = jdbc.queryForMap("SELECT full_name, status FROM users WHERE id=?", token.userId());
            if (!"ACTIVE".equals(user.get("status"))) {
                jdbc.update("UPDATE refresh_tokens SET revoked_at=COALESCE(revoked_at,now()) WHERE family_id=?", token.familyId());
                return Outcome.failure(invalidCredentials());
            }
            List<String> roles = jdbc.queryForList("SELECT role_code FROM user_roles WHERE user_id=? ORDER BY role_code", String.class, token.userId());
            return Outcome.success(createSession(token.userId(), (String) user.get("full_name"), roles, token.familyId()));
        });
        return unwrap(outcome);
    }

    public void logout(String presentedToken) {
        transaction.executeWithoutResult(status -> jdbc.update("UPDATE refresh_tokens SET revoked_at=COALESCE(revoked_at, now()) WHERE token_hash=?", sha256(presentedToken)));
    }

    private static AuthModels.TokenResponse unwrap(Outcome outcome) {
        if (outcome == null) throw new IllegalStateException("Identity transaction returned no result");
        if (outcome.failure() != null) throw outcome.failure();
        return outcome.token();
    }

    private AuthModels.TokenResponse createSession(UUID userId, String fullName, List<String> roles, UUID familyId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessMinutes, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(userId.toString()).audience(List.of(audience))
                .issuedAt(now).expiresAt(expiresAt).claim("roles", roles).claim("jti", UUID.randomUUID().toString()).build();
        String access = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("clinic-signing-key-v1").build(), claims)).getTokenValue();
        byte[] random = new byte[48];
        RANDOM.nextBytes(random);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        jdbc.update("INSERT INTO refresh_tokens(family_id,user_id,token_hash,expires_at) VALUES(?,?,?,?)",
                familyId, userId, sha256(refresh), now.plus(refreshDays, ChronoUnit.DAYS).atOffset(java.time.ZoneOffset.UTC));
        return new AuthModels.TokenResponse(access, "Bearer", accessMinutes * 60L, refresh, userId.toString(), fullName, roles);
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng.");
    }
    private ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Phiên đăng nhập hết hạn hoặc không hợp lệ.");
    }
    private static byte[] sha256(String input) {
        try { return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private record TokenRow(UUID id, UUID familyId, UUID userId, Instant expiresAt, Instant usedAt, Instant revokedAt) {}
    private record Outcome(AuthModels.TokenResponse token, ApiException failure) {
        static Outcome success(AuthModels.TokenResponse token) { return new Outcome(token, null); }
        static Outcome failure(ApiException failure) { return new Outcome(null, failure); }
    }
}

