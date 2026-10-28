package vn.phongkham.identity.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public AuthModels.TokenResponse login(@Valid @RequestBody AuthModels.LoginRequest request) { return authService.login(request); }
    @PostMapping("/refresh")
    public AuthModels.TokenResponse refresh(@Valid @RequestBody AuthModels.RefreshRequest request) { return authService.refresh(request.refreshToken()); }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody AuthModels.LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

}

