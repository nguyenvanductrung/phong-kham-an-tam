package vn.phongkham.identity.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import vn.phongkham.identity.persistence.AuthDao;
import vn.phongkham.identity.persistence.AuthDao.*;
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
public class AuthServiceImpl implements AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AuthDao dao;
    private final TransactionTemplate transaction;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;
    private final int accessMinutes;
    private final int refreshDays;

    public AuthServiceImpl(AuthDao dao, PlatformTransactionManager transactionManager, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       @Value("${security.issuer}") String issuer,
                       @Value("${security.audience}") String audience,
                       @Value("${security.access-token-minutes}") int accessMinutes,
                       @Value("${security.refresh-token-days}") int refreshDays) {
        this.dao = dao;
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
            List<Map<String, Object>> rows = dao.findUserForLogin(request.email());
            if (rows.isEmpty()) {
                passwordEncoder.matches(request.password(), passwordEncoder.encode("invalid-account"));
                return Outcome.failure(invalidCredentials());
            }
            Map<String, Object> user = rows.getFirst();
            UUID userId = (UUID) user.get("id");
            Instant lockedUntil = user.get("locked_until") == null ? null : ((java.sql.Timestamp) user.get("locked_until")).toInstant();
            if (!"ACTIVE".equals(user.get("status")) || (lockedUntil != null && lockedUntil.isAfter(Instant.now()))
                    || !passwordEncoder.matches(request.password(), (String) user.get("password_hash"))) {
                dao.recordFailedLogin(userId);
                return Outcome.failure(invalidCredentials());
            }
            dao.recordSuccessfulLogin(userId);
            List<String> roles = dao.findRoles(userId);
            return Outcome.success(createSession(userId, (String) user.get("full_name"), roles, UUID.randomUUID()));
        });
        return unwrap(outcome);
    }

    public AuthModels.TokenResponse refresh(String presentedToken) {
        byte[] hash = sha256(presentedToken);
        Outcome outcome = transaction.execute(status -> {
            var found = dao.findRefreshToken(hash);
            if (found.isEmpty()) return Outcome.failure(invalidRefresh());
            TokenRow token = found.getFirst();
            if (token.usedAt() != null || token.revokedAt() != null) {
                dao.revokeFamily(token.familyId());
                return Outcome.failure(new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSE", "Phiên đăng nhập đã bị thu hồi. Vui lòng đăng nhập lại."));
            }
            if (!token.expiresAt().isAfter(Instant.now())) {
                dao.revokeToken(token.id());
                return Outcome.failure(invalidRefresh());
            }
            dao.consumeToken(token.id());
            var user = dao.findUser(token.userId());
            if (!"ACTIVE".equals(user.get("status"))) {
                dao.revokeExpiredFamily(token.familyId());
                return Outcome.failure(invalidCredentials());
            }
            List<String> roles = dao.findCurrentRoles(token.userId());
            return Outcome.success(createSession(token.userId(), (String) user.get("full_name"), roles, token.familyId()));
        });
        return unwrap(outcome);
    }

    public void logout(String presentedToken) {
        transaction.executeWithoutResult(status -> dao.revokePresentedToken(sha256(presentedToken)));
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
        dao.insertRefreshToken(familyId, userId, sha256(refresh), now.plus(refreshDays, ChronoUnit.DAYS).atOffset(java.time.ZoneOffset.UTC));
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

    private record Outcome(AuthModels.TokenResponse token, ApiException failure) {
        static Outcome success(AuthModels.TokenResponse token) { return new Outcome(token, null); }
        static Outcome failure(ApiException failure) { return new Outcome(null, failure); }
    }
}
