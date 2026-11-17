package cn.edu.sar.web.access;

import jakarta.validation.constraints.*;
import java.util.List;

public final class AccessCommands {
    private AccessCommands() {}
    public record Register(@NotBlank @Pattern(regexp="[A-Za-z0-9]{6,20}",message="账号为6至20位字母或数字") String account,
        @NotBlank @Pattern(regexp="(?s)(?=.*[A-Za-z])(?=.*[0-9]).{8,32}",message="密码为8至32位，需包含字母和数字") String password,
        @NotBlank @Size(min=2,max=20) String nickname) {}
    public record Login(@NotBlank @Size(max=64) String account,@NotBlank @Size(max=128) String password) {}
    public record Profile(@NotBlank @Size(min=2,max=20) String nickname,@NotNull @Size(max=100) String contact,@NotNull @PositiveOrZero Long expectedVersion) {}
    public record Password(@NotBlank @Size(max=128) String oldPassword,
        @NotBlank @Pattern(regexp="(?s)(?=.*[A-Za-z])(?=.*[0-9]).{8,32}",message="新密码为8至32位，需包含字母和数字") String newPassword,
        @NotNull @PositiveOrZero Long expectedVersion) {}
    public record Apply(@NotBlank @Size(min=10,max=500) String material,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") String requestKey) {}
    public record Review(@NotBlank @Pattern(regexp="APPROVED|REJECTED") String decision,@NotBlank @Size(min=10,max=500) String reason,@NotNull @PositiveOrZero Long expectedVersion) {}
    public record Roles(@NotNull @Size(max=3) List<@NotNull @Pattern(regexp="SYSTEM_ADMIN|RESCUE_ADMIN|ADOPTION_ADMIN") String> roles,
        @NotBlank @Size(min=10,max=500) String reason,@NotNull @PositiveOrZero Long expectedVersion) {}
    public record Suspend(@NotBlank @Size(min=10,max=500) String reason,@NotNull @PositiveOrZero Long expectedVersion) {}
}
