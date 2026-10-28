package vn.phongkham.identity.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AuthModels {
    private AuthModels() {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record LogoutRequest(@NotBlank String refreshToken) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken, String userId,
                                String fullName, java.util.List<String> roles) {}
}

