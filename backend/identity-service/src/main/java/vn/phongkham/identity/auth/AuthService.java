package vn.phongkham.identity.auth;


/** Application use cases consumed by controllers and other services. */
public interface AuthService {
    AuthModels.TokenResponse login(AuthModels.LoginRequest request);
    AuthModels.TokenResponse refresh(String presentedToken);
    void logout(String presentedToken);

}
