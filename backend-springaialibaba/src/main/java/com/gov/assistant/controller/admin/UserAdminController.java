package com.gov.assistant.controller.admin;

import com.gov.assistant.entity.auth.UserAccount;
import com.gov.assistant.repository.auth.UserAccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
/**
 * 人员管理接口。
 *
 * 调用链：
 * 管理后台人员管理页 -> UserAdminController
 * -> UserAccountRepository/PasswordEncoder。
 */
public class UserAdminController {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 查询系统用户列表。
     *
     * 调用链：
     * 人员管理页 -> GET /api/admin/users
     * -> UserAccountRepository.findAllByOrderByCreatedAtDesc()。
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(userAccountRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(UserResponse::from)
                .toList());
    }

    /**
     * 新建系统用户。
     *
     * 调用链：
     * 人员管理页 -> POST /api/admin/users
     * -> UserAccountRepository.existsByUsername()
     * -> PasswordEncoder.encode()
     * -> UserAccountRepository.save()。
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (userAccountRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        UserAccount user = UserAccount.builder()
                .username(request.username().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName().trim())
                .role(request.role())
                .status(UserAccount.Status.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return ResponseEntity.ok(UserResponse.from(userAccountRepository.save(user)));
    }

    /**
     * 更新用户启用/停用状态。
     *
     * 调用链：
     * 人员管理页 -> PATCH /api/admin/users/{id}/status
     * -> UserAccountRepository.findById() -> UserAccountRepository.save()。
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> updateStatus(@PathVariable Long id,
                                                     @RequestBody Map<String, String> request) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setStatus(UserAccount.Status.valueOf(request.get("status")));
        user.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(UserResponse.from(userAccountRepository.save(user)));
    }

    /**
     * 重置用户密码。
     *
     * 调用链：
     * 人员管理页 -> PATCH /api/admin/users/{id}/password
     * -> UserAccountRepository.findById()
     * -> PasswordEncoder.encode()
     * -> UserAccountRepository.save()。
     */
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setUpdatedAt(LocalDateTime.now());
        userAccountRepository.save(user);
        return ResponseEntity.ok().build();
    }

    public record CreateUserRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "姓名不能为空") String displayName,
            @NotBlank(message = "密码不能为空") String password,
            @NotNull(message = "角色不能为空") UserAccount.UserRole role
    ) {
    }

    public record ResetPasswordRequest(@NotBlank(message = "密码不能为空") String password) {
    }

    public record UserResponse(
            Long id,
            String username,
            String displayName,
            UserAccount.UserRole role,
            UserAccount.Status status,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt
    ) {
        public static UserResponse from(UserAccount user) {
            return new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRole(),
                    user.getStatus(),
                    user.getLastLoginAt(),
                    user.getCreatedAt()
            );
        }
    }
}
