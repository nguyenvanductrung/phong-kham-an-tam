package vn.phongkham.identity.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class UserAdminModels {
    private UserAdminModels() {}
    public record CreateUserRequest(@NotBlank @Email @Size(max=254) String email,
                                    @NotBlank @Size(max=200) String fullName,
                                    @NotBlank @Size(min=12,max=128) String password,
                                    @NotEmpty List<@NotBlank String> roles) {}
    public record RolesRequest(@NotEmpty List<@NotBlank String> roles) {}
    public record StatusRequest(@NotBlank String status) {}
    public record UserResponse(String id,String email,String fullName,String status,List<String> roles,Instant createdAt) {}
}
